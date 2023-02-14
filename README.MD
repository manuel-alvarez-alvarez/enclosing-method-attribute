# Bytebuddy and EncosingMethod

Bytebuddy makes use of the [EncosingMethod attribute](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.7) 
while compiling the method graph of a class to discover the bounds of generic types in enclosed classes, 
check [org.glassfish.jersey.server.internal.inject.ParamConverters$StringConstructor$1](https://github.com/eclipse-ee4j/jersey/blob/master/core-common/src/main/java/org/glassfish/jersey/internal/inject/ParamConverters.java#L100):

```java
    @Singleton
    public static class StringConstructor implements ParamConverterProvider {

        @Override
        public <T> ParamConverter<T> getConverter(final Class<T> rawType,
                                                  final Type genericType,
                                                  final Annotation[] annotations) {

            final Constructor constructor = AccessController.doPrivileged(ReflectionHelper.getStringConstructorPA(rawType));

            return (constructor == null) ? null : new AbstractStringReader<T>() {

                @Override
                protected T _fromString(final String value) throws Exception {
                    return rawType.cast(constructor.newInstance(value));
                }
            };
        }

    }
```

During the instrumentation phase, two different things can happen:

1. The class has not yet been loaded so the transformation uses the original .class file byte array:

```shell
./gradlew run
```

The *EnclosingMethod* attribute is present in the bytecode and as such bytebuddy will be able to analyze the method graph:

```
OuterClass [owner:org/glassfish/jersey/server/internal/inject/ParamConverters$StringConstructor, name: getConverter, descriptor: (Ljava/lang/Class;Ljava/lang/reflect/Type;[Ljava/lang/annotation/Annotation;)Ljavax/ws/rs/ext/ParamConverter;]
```
 
2. The class has been previously loaded and needs to be retransformed::

```shell
./gradlew run_NOOK
```

This time the JVM has to generate the byte code from the internal representation of the class in memory 
(check [jvmtiClassFileReconstituter.cpp](https://github.com/openjdk/jdk8u/blob/master/hotspot/src/share/vm/prims/jvmtiClassFileReconstituter.cpp)),
but it does not take into account the EnclosingMethod attribute that it's now present in the `java.lang.Class` object but missing in the bytecode:

```
Retransformation [enclosing: public javax.ws.rs.ext.ParamConverter org.glassfish.jersey.server.internal.inject.ParamConverters$StringConstructor.getConverter(java.lang.Class,java.lang.reflect.Type,java.lang.annotation.Annotation[])]
```

So in this case bytebuddy is very like to fail with:

```
java.lang.IllegalStateException: Cannot resolve bounds of unresolved type variable T by class org.glassfish.jersey.server.internal.inject.ParamConverters$StringConstructor$1
	at net.bytebuddy.pool.TypePool$Default$LazyTypeDescription$GenericTypeToken$ForTypeVariable$UnresolvedTypeVariable.getUpperBounds(TypePool.java:4549)
	at net.bytebuddy.description.type.TypeDescription$Generic$OfTypeVariable.asErasure(TypeDescription.java:5610)
	at net.bytebuddy.description.method.MethodDescription$AbstractBase.asTypeToken(MethodDescription.java:910)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default$Key$Harmonized.of(MethodGraph.java:1005)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default$Key$Store.registerTopLevel(MethodGraph.java:1190)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default.doAnalyze(MethodGraph.java:717)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default.analyze(MethodGraph.java:679)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default.doAnalyze(MethodGraph.java:715)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default.analyze(MethodGraph.java:679)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default.analyzeNullable(MethodGraph.java:698)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default.doAnalyze(MethodGraph.java:712)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$Default.compile(MethodGraph.java:637)
	at net.bytebuddy.dynamic.scaffold.MethodGraph$Compiler$AbstractBase.compile(MethodGraph.java:519)
	at net.bytebuddy.dynamic.scaffold.MethodRegistry$Default.prepare(MethodRegistry.java:472)
	at net.bytebuddy.dynamic.scaffold.inline.RedefinitionDynamicTypeBuilder.toTypeWriter(RedefinitionDynamicTypeBuilder.java:203)
	at net.bytebuddy.dynamic.DynamicType$Builder$AbstractBase$UsingTypeWriter.make(DynamicType.java:4057)
	at net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer.doTransform(AgentBuilder.java:12106)
	at net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer.transform(AgentBuilder.java:12041)
	at net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer.access$1800(AgentBuilder.java:11758)
	at net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer$LegacyVmDispatcher.run(AgentBuilder.java:12441)
	at net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer$LegacyVmDispatcher.run(AgentBuilder.java:12381)
	at java.security.AccessController.doPrivileged(Native Method)
	at net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer.doPrivileged(AgentBuilder.java)
	at net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer.transform(AgentBuilder.java:11950)
...	
```
