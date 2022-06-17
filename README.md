Build

```shell
# export JAVA_HOME, must be Java 11

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.15.jdk/Contents/Home

# chmod on gradlew and  run target buildPlugin

chmod +x gradlew 

./gradlew tasks buildPlugin
```

plugin jar should be in
`./build/libs/subsets-selector-1.0.SNAPSHOT.jar`

or 

`./build/idea-sandbox/plugins/subsets-selector/lib/subsets-selector-1.0.SNAPSHOT.jar`

Both files look the same to me

