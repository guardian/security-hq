# Snyk

## Introduction

Snyk is a managed service which uses a dependency analysis tool and a vulnerability database to inform you of
vulnerabilities in your application.

## Getting set up

We use Enterprise SSO to get into Snyk, tied to our office email accounts. This requires entering via a special link:

    %SNYK_SSO_LINK%

This may not work if you are already signed into another Snyk account, so you may need to logout first.

We have an enterprise group called 'The Guardian'. Everyone will get added to the organisation `guardian-people`
(under our enterprise group) when they first log in. Once team members have signed in, they can be added
to other organisations for each team. However, other organisations will not appear in the dropdown menu until
you have been added to them.

This needs to be done by a group admin (a few individuals or the InfoSec team) or an admin of the desired
organisation. You will probably also want to upgrade team members from collaborators to admins (in the members
section of the dashboard).

## Integrating Snyk with your project(s)

By far the easiest way to use Snyk to test a project is to use their Github integration. 

In [the Snyk dashboard](https://snyk.io/) choose the organisation that the project should be part of,
you can add a new project from there. You will need to be an administrator of the Github repository to integrate
Snyk so that it can create a webhook to re-test the project for each Pull Requests.

## Eliminating Vulnerabilities

Clearly the best way to remove a vulnerability is to use a version of the library which does not have the vulnerability.
This is usually achieved by upversioning (or in rarer cases downversioning).

Alternatively, if a secure version of a library is not available, and an alternative is not found, it is possible to
ignore the vulnerability temporarily.  This approach should be used with caution. Never ignore a vulnerability forever -
at least make sure you will be told when a fix is available.

### Upversioning

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
+  "my.library" %% "cleverstuff" % "1.0.0"
 )

```

### Alternatives

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

## Exemptions

The worst case scenario is that the library is still vulnerable, has no alternatives, and cannot be removed.  You
may wish to build and release anyway, ideally reporting the situation to a risk register. This is most likely to happen when
a new vulnerability is discovered and the library publisher has not had chance to respond.

For private repositories, this is cleanly achieved by creating a `.snyk` file which can act both as an exemption and the risk register itself.
Please try to give meaningful reasons for allowing the exemption.

The `.snyk` file can then be added to the repository and the build should continue.

__For public repositories, this would mean that the vulnerability is effectively advertised right in
the project!__  This may be acceptable, as any attacker could equally scan the project for themselves.

When an exemption expires, the build will start to fail again (see Reviewing Expired Exemptions below).
If a review still finds no mitigation available, then it is trivial to extend by changing the date and committing.
Therefore repeated exemptions of no more than a month or two are preferred over a single long exemption.

### `.snyk` file format

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

### Reviewing Expired Exemptions

When reviewing previously accepted risks, it would be prudent to examine the git history to discover how long a problem has existed.
If a problem is not fairly new, and still looks unlikely to be resolved, then reconsider both the _risk_ of exploitation and
the _cost_ of exploitation before deciding if it is approprate to continue extending an exemption.  It may now be
appropriate to consider alternatives.
