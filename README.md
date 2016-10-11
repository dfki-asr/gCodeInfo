GCodeInfo
=========

This is a fork from [GCodeInfo]{https://github.com/dietzm/GCodeInfo}.
For more information, please see there.

All that is done here, is to convert GCodeInfo into more Maven project.

Install GCodeInfo into your local Maven repo via
```
mvn clean package install
```

and add a dependency to your project's `pom.xml` like so
```xml
<dependency>
	<groupId>de.dietzm</groupId>
	<artifactId>GCodeInfo</artifactId>
	<version>0.1</version>
</dependency>
``` 

Happy gCode analyzing!