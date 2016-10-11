GCodeInfo
=========

This is a fork from [GCodeInfo](https://github.com/dietzm/GCodeInfo).
For more information and for giving some backslapping, please see [there](https://github.com/dietzm/GCodeInfo)!

All that is done here, is to mavenize GCodeInfo.

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
