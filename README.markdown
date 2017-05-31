# export-dependencies-plugin

[![Build Status](https://travis-ci.org/evosec/export-dependencies-maven-plugin.svg?branch=develop)](https://travis-ci.org/evosec/export-dependencies-maven-plugin)

This is a maven plugin that exports the dependencies to other formats.

The only format that is currently supported is [buck](https://buckbuild.com/).

So for example it converts

~~~~
<project
    xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>de.evosec</groupId>
    <artifactId>buck-single-dependency</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>18.0</version>
        </dependency>
    </dependencies>
</project>
~~~~

to

~~~~~
java_library(
  name = 'COMPILE',
  visibility = ['PUBLIC'],
  exported_deps = [
    ':com.google.guava-guava-jar'
  ],
)

java_library(
  name = 'OPTIONAL',
  visibility = ['PUBLIC'],
  exported_deps = [
    
  ],
)

java_library(
  name = 'PROVIDED',
  visibility = ['PUBLIC'],
  exported_deps = [
    
  ],
)

java_library(
  name = 'RUNTIME',
  visibility = ['PUBLIC'],
  exported_deps = [
    
  ],
)

java_library(
  name = 'TEST',
  visibility = ['PUBLIC'],
  exported_deps = [
    
  ],
)

prebuilt_jar(
  name = 'com.google.guava-guava-jar',
  binary_jar = ':remote-com.google.guava-guava-jar',
)

remote_file(
  name = 'remote-com.google.guava-guava-jar',
  out = 'com.google.guava-guava-jar-18.0.jar',
  url = 'mvn:com.google.guava:guava:jar:18.0',
  sha1 = 'cce0823396aa693798f8882e64213b1772032b09',
)
~~~~

Then you can use something like the following BUCK file to build your project. That way you can use maven and BUCK in parallel. This eases migration and you can continue using maven to manage your dependencies. Just make sure to call `export-dependencies:buck` before running `buck`.

~~~
java_library(
    name = "main",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**"]),
    resources_root = "src/main/resources",
    exported_deps = [
    	"//target:COMPILE",
    ],
    deps = [
        "//target:OPTIONAL",
    ],
    provided_deps = [
    	"//target:PROVIDED",
    ],
)

java_test(
    name = "test",
    srcs = glob(["src/test/java/**/*Test.java"]),
    resources = glob(["src/test/resources/**"]),
    resources_root = "src/test/resources",
    source_under_test = [":main"],
    deps = [
        ":main",
        ":test_utils",
        "//target:TEST",
        "//target:OPTIONAL",
        "//target:PROVIDED",
    ],
)

java_library(
    name = "test_utils",
    srcs = glob(["src/test/java/**/*.java"], excludes = ["**/*Test.java"]),
    deps = [
    	":main",
    	"//target:TEST",
    	"//target:OPTIONAL",
        "//target:PROVIDED",
    ],
)
~~~~
