package george.app;


/* This class locates the newest version of the JAR, and loads it.  It is pure Java, to avoid touching any Clojure RT with class loaders etc. */

public class Loader {

public static void main(String [] args) {
    System.out.println("george.app.Loader.main");
    Starter.main(args);
}
}