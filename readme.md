# HAPI FHIR interceptor for validation

- 這是讓 HAPI FHIR 成為 TX Server 的 interceptor


## 如何 debugging
- 將 hapi-fhir-jpaserver-starter clone 並放到此專案內
- 更改 hapi-fhir-jpaserver-starter/pom.xml 的 parent
```xml
<parent>
    <groupId>org.cylab</groupId>
    <artifactId>cylab-hapi-fhir-jpaserver-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</parent>
```
- 更改 hapi-fhir-jpaserver-starter/pom.xml 的 hapi.version
```xml
<properties>
    <java.version>17</java.version>
    <hapi.version>7.4.0</hapi.version>
</properties>
```
- 在 hapi-fhir-jpaserver-starter/pom.xml 的 dependencies 中加入 interceptor 的 dependency
```xml
<dependency>
    <groupId>org.cylab</groupId>
    <artifactId>cy-hapi-Interceptors</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 更改所有的 `${project.parent.version}` 為 `${hapi.version}`

