# How to Build

To build the jar with the plugin execute the commands below

```shell
# export JAVA_HOME, must be Java 11

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.15.jdk/Contents/Home

# chmod on gradlew and  run target buildPlugin

chmod +x gradlew 

./gradlew tasks buildPlugin
```

## Where is the .jar file with plugin?

When the build completes plugin jar should be in


1. `./build/libs/subsets-selector-1.0.SNAPSHOT.jar` or

# How to deploy into Idea

Open "Plugins" dialog and press on the "Gear" button, then select "Install from
file" and select the jar file with the plugin
