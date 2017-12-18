# Snyk

## Introduction

Snyk is a managed service which uses a dependency analysis tool and a vulnerability database to inform you of
vulnerabilities in your application.

## Installing

Snyk requires node, and `npm`.  To install on a mac, please use:

```
brew install npm
npm install -g snyk
```

Snyk then requires an authentication token to be set up.  Github auth is almost certainly most appropriate.

```
snyk auth
```

# Usage

## Typical Usage for Applications

In the build process (eg TeamCity), you can add a build step to check with the Snyk database for vulnerabilities.

This information can be simply treated as reporting information, or can be used to fail a build if the return
code is non-zero (ie at least one vulnerability found or error).

## Use with Scala

### Pre-requisites

Snyk needs to first create a dependency tree for your project (based on the sbt dependencies), then check with the
Snyk database.

Dependency graph creation is via an sbt plugin.  Add the following line to your project plugins file or your `.sbt/plugins`
directory:


```
/Users/<you>/.sbt/<sbt-version>/plugins/snyk.sbt
or
<projectroot>/project/plugins.sbt
```

```
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
```

### Invocation

It is recommended that `sbt` itself is invoked first, to confirm that the build file is good.  You
can then invoke `snyk` as follows.  If you are working on a new snyk implementation, then it is often
worth cutting down the on-screen noise by using `--show-vulnerable-paths=false`.

```
sbt test
snyk test --file=build.sbt [--show-vulnerable-paths=(true|false)]
```

### Gotchas

#### With SBT 1.0.0+

It appears that sbt expects to invoke `dependency-tree`.  After sbt 1.0, this command appears to be `dependencyTree`.

As Snyk is written in an interpreted script, this can be 'fixed' for now (if you use sbt > 1.0.0) using the following (mac) command:

```
sed -i '' 's/dependency-tree/dependencyTree/g' $(grep -wrl 'dependency-tree' /usr/local/lib/node_modules/snyk/node_modules/snyk-sbt-plugin/)
```

And to revert:
```
sed -i '' 's/dependencyTree/dependency-tree/g' $(grep -wrl 'dependencyTree' /usr/local/lib/node_modules/snyk/node_modules/snyk-sbt-plugin/)
```

#### With bugs in build.sbt

If there is a bug in build.sbt, snyk is likely to get stuck on the `Querying vulnerabilities database...` stage.  Therefore
it is suggested that you always invoke `sbt test` or similar first.  If  you see the `Querying vulnerabilities database...`
step for more than ten seconds or so, you should check your `build.sbt` is good.

## Use with Nodejs

### Pre-requisites

Ensure your node packages are up to date with the following:

```
rm node_modules
npm install
```

### Invocation

Scan for vulnerabilities with:

```
snyk test --file=package.json
```

Note that by default, Snyk will only scan for the dependencies found in the `dependencies` section.  It may be relevant
to you to check for dependencies in development, which will only happen if you scan the `devDependencies` section using
the `--dev` flag:

```
snyk test --file=package.json --dev
```

### Optional Magic

Snyk provides a node-only `protect` command which will forcibly alter dependencies to remove vulnerabilities.  This
requires setting up default locations for fetching libraries and rulesets for version changes, which is achieved using:

```
snyk wizard
```

And then answering questions.  Protection can then be requested using:

```
snyk protect
```

# Eliminating Vulnerabilities

Clearly the best way to remove a vulnerability is to use a version of the library which does not have the vulnerability.
This is usually achieved by upversioning (or in rarer cases downversioning).

Alternatively, if a secure version of a library is not available, and an alternative is not found, it is possible to
ignore the vulnerability temporarily.  This approach should be used with caution!

## Upversioning

Note that examples are all for sbt projects, however nodejs and other projects will be similar.

When you visit a vulnerability report from the output (eg https://snyk.io/vuln/SNYK-JAVA-COMFASTERXMLJACKSONCORE-31573),
you may see a 'Remediation' section at the bottom.  If not, you will have to read the text and decide for yourself!

If there is a known non-vulnerable version listed, you can simply change the dependency declaration as below:

```
$ git diff build.sbt
diff --git a/build.sbt b/build.sbt
index 434dbb2..d123098 100644
--- a/build.sbt
+++ b/build.sbt
@@ -29,7 +29,11 @@ libraryDependencies ++= Seq(
   "org.typelevel" %% "cats" % "0.8.1",
-  "my.library" %% "cleverstuff" % "0.0.99"
+  "my.library" %% "cleverstuff" % "1.0.0+"
 )

```

## Alternatives

If there is no non-vulnerable version of the library, consider using an alternative.  This, of course, is likely to mean more
work altering the calling code, and so is not the preferred solution.

```
$ git diff build.sbt
diff --git a/build.sbt b/build.sbt
index 434dbb2..d123098 100644
--- a/build.sbt
+++ b/build.sbt
@@ -29,7 +29,11 @@ libraryDependencies ++= Seq(
   "org.typelevel" %% "cats" % "0.8.1",
-  "my.library" %% "quickanddirty" % "0.0.3"
+  "his.library" %% "polished" % "1.1.0+"
 )

```

# Exemptions

The worst case scenario is that the library is still vulnerable, has no alternatives, and cannot be removed.  You
may wish to build and release anyway, ideally reporting the situation to a risk register. This is most likely to happen when
a new vulnerability is discovered and the library publisher has not had chance to respond.

This is cleanly achieved by creating a `.snyk` file which can act both as an exemption and the risk register itself.
Please try to give meaningful reasons for allowing the exemption.

The `.snyk` file can then be added to the repository and the build should continue.

When an exemption expires, the build will start to fail again (see Reviewing Expired Exemptions below).
If a review still finds no mitigation available, then it is trivial to extend by changing the date and committing.
Therefore repeated exemptions of no more than a month or two are preferred over a single long exemption.

## `.snyk` file format

See https://support.snyk.io/frequently-asked-questions/finding-vulnerabilities/how-can-i-ignore-a-vulnerability

```
version: v1.5.0
ignore:
  VULNERABILITY-LABEL:
    - '* > PACKAGE-PROVIDER:PACKAGE':
      reason: REASON
      expires: ISO8601-DATE
```
eg
```
version: v1.5.0
  'SNYK-JAVA-XERCES-31497':
    - '* > xerces:xercesImpl':
      reason: 'Vulnerability requires local access, and this is an internal service'
      expires: 2018-01-11T00:00:00Z
  'SNYK-JAVA-ORGAPACHEHTTPCOMPONENTS-31517':
    - '* > org.apache.httpcomponents:httpclient':
      reason: 'No remediation available; moving to HTTPS anyway'
      expires: 2018-01-11T00:00:00Z
```

Note that the entry includes an expiry date.  Good practice would suggest that this should be of reasonable short duration.
Long expiry times will mask problems.

## Reviewing Expired Exemptions

When reviewing accepted risks, it would be prudent to examine the git history to discover how long a problem has existed.
If a problem is not fairly new, and still looks unlikely to be resolved, then reconsider both the _risk_ of exploitation and
the _cost_ of exploitation before deciding if it is approprate to continue extending an exemption.  It may now be
appropriate to consider alternatives.
