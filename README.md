# simplefu
simple file util for resp.me deployments.

Designed with a focus on reliability and convenience, rather than performance or scalability, this solution is particularly suitable for automation tasks involving file movement, copying, or backup operations.

![alt text](./assets/simplefu.png "Simplefu")

## Usage

```bash
./gradle shadowJar
```

or jbang counterpart:

```
jbang DeployUtil.java
```

file list file:
```text
/some/path/tofile/a.txt -> /another/path/
/some/path/tozipfile/a.zip!x/y/z.txt -> /another/path/
/some/path/tozipfile/a.zip!~y.txt -> /another/path/
```

! split will extract the exactly file from the zip archive, !~ will extract the first file endswith the name.

## command
copy files;
```bash
java -jar simplefu.jar copyfilelist copy-always-list.txt 
```

rollback files;
```bash
java -jar simplefu.jar rollback copy-always-list.txt
```

backup all the files about copy to.
```bash
java -jar simplefu.jar backup --upload-to-azure --backup-to some.zip file-list-file1, file-list-file2 ...
```
restore files at the destination

```bash
java -jar simplefu.jar restore --restore-from some.zip file-list-file1, file-list-file2 ...
```

## delombok

java -jar C:\Users\jiang\.m2\repository\org\projectlombok\lombok\1.18.22\lombok-1.18.22.jar delombok app\src -d app\src-delomboked

Set-Item -Path Env:\https_proxy -Value 'socks5h://127.0.0.1:7890'

## gradle

./gradlew -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890

modify:
gradle-wrapper.properties

distributionUrl=gradle-8.2.1-bin.zip

ls ~/.gradle/caches/modules-2/files-2.1/