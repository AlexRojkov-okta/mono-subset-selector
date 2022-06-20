## How to Build

To build the jar with the plugin execute the commands below

```shell
# export JAVA_HOME, must be Java 11
sdk use 11.0.4.11.1-amzn

# chmod on gradlew and  run target buildPlugin
chmod +x gradlew 
./gradlew tasks buildPlugin
```

### Where is the .jar file with plugin?

When the build completes plugin jar should be in
```
./build/libs/subsets-selector-1.0.SNAPSHOT.jar
```

## How to deploy into Idea

Open "Plugins" dialog and press on the "Gear" button, then select "Install from
file" and select the jar file with the plugin.

By default you must be on IntelliJ 2020.3.4
