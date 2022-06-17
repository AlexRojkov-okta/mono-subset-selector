to build

```shell
# export JAVA_HOME, must be Java 11

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.15.jdk/Contents/Home

# run build plugin target
chmod +x gradlew 
./gradlew tasks buildPlugin
```

plugin jar should be in
`./build/libs/subsets-selector-1.0.1.jar`

or `./build/idea-sandbox/plugins/subsets-selector/lib/subsets-selector-1.0.1.jar`

both files look the same to me

