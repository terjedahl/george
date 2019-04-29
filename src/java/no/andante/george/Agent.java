/*
 *  Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
 * The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
 *  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
 *  You must not remove this notice, or any other, from this software.
 */

package no.andante.george;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.ListIterator;

// Requires [org.ow2.asm/asm "7.1"] and [org.ow2.asm/asm-tree "7.1"]
// OBS! [org.clojure/core.async "0.4.490"] -> [org.clojure/tools.analyzer.jvm "0.7.2"] -> [org.ow2.asm/asm-all "4.2"]
// OBS! There is also a java.base/jdk.internal.org.objectweb.asm.***
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.tree.AbstractInsnNode.*;

/*
This agent optionally does one thing:

When the environment variable APPLICATION_NAME is set, it will apply certain transformations to
the JavaFX class 'com.sun.glass.ui.Application' such that the variable's value is the one used by GTK on Ubuntu as
the application name and will match StartupWMClass in a .desktop file if it is set to the same.

This is done by setting the constant DEFAULT_NAME to the variable, and altering the constructor method such that
the variable 'name' is set from the constant in stead of from the compiled/inline constant pool.
Also, the method 'setName' is "deactivated" by removing its body, so that 'name' doesn't get set to something else later.

Usage:
1. Ensure you have the Java module 'java.instrument' (standard in JRE/JDK or included in your custom JRE build)
2. Ensure that ASM is on your classpath.  (See Maven coordinate above)
3. Compile this file into your project.
4. Ensure your JAR manifest contains the key-values:
     Premain-Class            no.andante.george.Agent
     Can-Retransform-Classes  true
5. In your java launch command, include "-javaagent:<path-to-your-jar>"
6. Set the environment variable - in your launch script or as part of your call:
     export APPLICATION_NAME=<your-application-name>
     java ....
     # or
     APPLICATION_NAME=<your-application-name> java ....
7. If you have a .desktop-file for your application, set the key-value:  StartupWMClass=<your-application-name>

Note:
- This solution has only been tested with Java 11.0.2 and JavaFX 11.0.2 on Ubuntu (and Mac)
- You may extract this single file into your project as-is, and keeping in place the inline copyright notice.

To learn more about ASM, the User guide is excellent:  https://asm.ow2.io/versions.html
*/


public class Agent {

    private final static boolean is_debug = System.getenv("DEBUG") != null || System.getenv("debug") != null;

    private final static String application_name = System.getenv("APPLICATION_NAME");


    private static void debug(String s) {
        if (is_debug)
            System.out.println("[DEBUG][Agent]: " + s);
    }


    public static void premain(String agentArg, Instrumentation instr){
        debug("APPLICATION_NAME: " + application_name);
        if(application_name != null)
            instr.addTransformer(new ApplicationNameTransformer(), true);
    }


    static class ApplicationNameTransformer implements ClassFileTransformer {

        public byte[] transform(ClassLoader cl, String name, Class c, ProtectionDomain pd, byte[] bytes) {
            if (!"com/sun/glass/ui/Application".equals(name))
                return bytes;

            debug("Found 'com/sun/glass/ui/Application'.");
            ClassNode node = new ClassNode(ASM4);
            ClassReader reader = new ClassReader(bytes);
            reader.accept(node, 0);
            transformApplication(node);
            ClassWriter writer = new ClassWriter(reader, 0);
            node.accept(writer);
            return writer.toByteArray();
        }


        private void transformApplication(ClassNode node) {
            @SuppressWarnings("unchecked")
            final List<FieldNode> fields = node.fields;
            for(FieldNode f: fields)
                if(f.name.equals("DEFAULT_NAME")) {
                    debug("Setting DEFAULT_NAME");
                    f.value = application_name;
                }

            @SuppressWarnings("unchecked") final List<MethodNode> methods = node.methods;
            for(MethodNode method: methods) {
                // Inactivate 'setName' method by replace the body with a single RETURN opcode
                if(method.name.equals("setName")) {
                    //Replace the body with a return statement.
                    InsnList insns = new InsnList();
                    insns.add(new InsnNode(RETURN));
                    method.instructions = insns;
                }
                // Have the 'Application' constructor set name from DEFAULT_NAME field instead of from compiled constant pool.
                else if(method.name.equals("<init>")) {
                    InsnList insns = method.instructions;
                    @SuppressWarnings("unchecked")
                    ListIterator<AbstractInsnNode> it = insns.iterator();
                     while(it.hasNext()) {
                         AbstractInsnNode insn = it.next();
                         if (insn.getType() == LDC_INSN) {
                            debug("Replacing LDC instruction with GETSTATIC");
                             insns.set(insn, new FieldInsnNode(GETSTATIC, node.name, "DEFAULT_NAME", Type.getType(String.class).getDescriptor()));
                         }
                     }
                }
            }
        }
    }
}
