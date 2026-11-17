# Theoria shell environment — kullanım: `source tools/env.sh`
# Global JAVA_HOME (~/.zshrc) LucidRem için Java 21'de kalır (Gradle 8.14 Java 25'te çalışmaz).
# Gradle zaten gradle/gradle-daemon-jvm.properties ile JDK 25 seçer; bu dosya yalnızca kabuktaki java/javac için.
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1
