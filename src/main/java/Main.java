import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Arrays;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Opcodes;

public class Main {

  private static final String CLASS_NAME =
      "org/glassfish/jersey/server/internal/inject/ParamConverters$StringConstructor$1";

  private static final String RETRANSFORM_ARG = "retransform";

  public static void main(final String[] args) throws Exception {
    Server server;
    if (Arrays.stream(args).anyMatch(RETRANSFORM_ARG::equalsIgnoreCase)) {
      server = startServer();
      instrument(true);
    } else {
      instrument(false);
      server = startServer();
    }
    doGet(server);
  }

  @SuppressWarnings("unchecked")
  private static Server startServer() throws Exception {
    final ClassLoader loader = Thread.currentThread().getContextClassLoader();
    final Class<? extends Server> clazz =
        (Class<? extends Server>) loader.loadClass("GrizzlyServer");
    final Server server = clazz.getDeclaredConstructor().newInstance();
    server.start();
    return server;
  }

  private static void instrument(final boolean retransform) throws Exception {
    final Instrumentation instrumentation = ByteBuddyAgent.install();
    instrumentation.addTransformer(
        new ClassFileTransformer() {
          @Override
          public byte[] transform(
              ClassLoader loader,
              String className,
              Class<?> classBeingRedefined,
              ProtectionDomain protectionDomain,
              byte[] classfileBuffer)
              throws IllegalClassFormatException {
            if (CLASS_NAME.equals(className)) {
              if (classBeingRedefined != null) {
                System.out.printf(
                    "Retransformation [enclosing: %s]%n", classBeingRedefined.getEnclosingMethod());
              }
              ClassReader reader = new ClassReader(classfileBuffer);
              reader.accept(
                  new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitOuterClass(
                        final String owner, final String name, final String descriptor) {
                      System.out.printf(
                          "OuterClass [owner:%s, name: %s, descriptor: %s]%n",
                          owner, name, descriptor);
                    }
                  },
                  ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            }
            return null;
          }
        },
        true);
    if (retransform) {
      final Class<?> target = Class.forName(CLASS_NAME.replaceAll("\\/", "."));
      instrumentation.retransformClasses(target);
    }
  }

  public static void doGet(final Server server) throws Exception {
    try {
      final URL url = new URL(String.format("%s/hello?name=Tom", server.getAddress()));
      final HttpURLConnection con = (HttpURLConnection) url.openConnection();
      if (con.getResponseCode() != 200) {
        throw new RuntimeException("Invalid response " + con.getResponseCode());
      }
    } finally {
      server.close();
    }
  }
}
