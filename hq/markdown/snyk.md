# Snyk

## Introduction

Snyk is a managed service which uses a dependency analysis tool and a vulnerability database to inform you of
vulnerabilities in your application.

## Getting set up

We use Enterprise SSO to get into Snyk, tied to our office email accounts. This requires entering via the following
special link.

[%SNYK_SSO_LINK%](%SNYK_SSO_LINK%)

This may not work if you are already signed into another Snyk account, so you may need to logout first.

We have an enterprise group called 'The Guardian'. Everyone will get added to the organisation `guardian-people`
(under our enterprise group) when they first log in. Once team members have signed in, they can be added
to other organisations for each team. However, other organisations will not appear in the dropdown menu until
you have been added to them.

This needs to be done by a group admin (a few individuals or the InfoSec team) or an admin of the desired
organisation. You will probably also want to upgrade team members from collaborators to admins (in the members
section of the dashboard).

## Integrating Snyk with your project(s)

By far the most effective way to integrate Snyk is through GitHub Actions. Here's how to do it:

1. Make sure your repo has the `SNYK_TOKEN` available under 'Organization secrets' (Go to Settings -> Secrets). If it does not, ask an engineering manager to enable it for your repo.
1. Create a file called `snyk.yml` in the `/.github/workflows/` folder of your repo. Paste one the appropriate code snippets from below into your new file.
1. Edit the `org` argument in the code snippet to your org's code. We have several organisations in our Snyk account. The code snippet bellow uses the Reader Revenue's code. To get your organisation's code go to the [Snyk dashboard](https://app.snyk.io/org/), select the org you want then obtain the code from the URL.
1. If your package file does not live in the main folder you will need to add a `--file` argument, such as the one in the Node example below. 

![image](https://user-images.githubusercontent.com/48949546/112194614-f6985880-8c00-11eb-946f-a88fdae57662.jpg)


Setting up this Action will take care of updating your project's entry in snyk.io daily or whenever a new push to `main` takes place. It will also add feedback to commits and PRs, showing developers if their branch has any security vulnerabilities.


### Node
```
# This action runs every day at 6 AM and on every push
# If the branch it's running on is main then it will run snyk monitor (reports vulnerabilities to snyk.io)
# Otherwise it will run snyk test
name: Snyk

on:
  schedule:
    - cron: "0 6 * * *"
  push:
  workflow_dispatch:

jobs:
  security:
    runs-on: ubuntu-latest
    env:
      SNYK_COMMAND: test
    steps:
      - name: Checkout branch
        uses: actions/checkout@v2

      - name: Set command to monitor
        if: github.ref == 'refs/heads/main'
        run: echo "SNYK_COMMAND=monitor" >> $GITHUB_ENV

      - name: Run Snyk to check for vulnerabilities
        uses: snyk/actions/node@0.3.0
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          args: --org=the-guardian-cuu --project-name=${{ github.repository }} --file=./app/yarn.lock
          command: ${{ env.SNYK_COMMAND }}

```

### Scala 

```
# This action runs every day at 6 AM and on every push
# If the branch it's running on is main then it will run snyk monitor (reports vulnerabilities to snyk.io)
# Otherwise it will run snyk test
name: Snyk

on:
  schedule:
    - cron: '0 6 * * *'
  push:
  workflow_dispatch:

jobs:
  security:
    runs-on: ubuntu-latest
    env:
      SNYK_COMMAND: test
    steps:
      - name: Checkout branch
        uses: actions/checkout@v2

      - name: Set command to monitor
        if: github.ref == 'refs/heads/main'
        run: echo "SNYK_COMMAND=monitor" >> $GITHUB_ENV

      - name: Run Snyk to check for vulnerabilities
        uses: snyk/actions/scala@0.3.0
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          args: --org=the-guardian-cuu --project-name=${{ github.repository }}
          command: ${{ env.SNYK_COMMAND }}
```

## Eliminating Vulnerabilities

The best way to remove a vulnerability is to use a version of the library which does not have the vulnerability.
This is usually achieved by upversioning (or in rarer cases downversioning).

If a secure version of a library is not available and an alternative is not found it is possible to
ignore the vulnerability temporarily. This approach should be used with caution!
Never ignore a vulnerability forever - at least make sure you will be told when a fix is available.

### Examples

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

If there is no non-vulnerable version of the library, consider using an alternative.

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

### Ignoring vulnerabilities

The worst case scenario is that the library is still vulnerable, has no alternatives, and cannot be removed.  You
may wish to build and release anyway, ideally reporting the situation to a risk register. This is most likely to happen when
a new vulnerability is discovered and the library publisher has not had chance to respond.

In this case you can create an exemption by ignoring the vulnerability. The easiest way to do this is to ignore it
from the Snyk dashboard. Do not ignore vulnerabilities forever, and ideally make sure you will be told when a fix
is available.

When an exemption expires, the test will start to fail again (see Reviewing Expired Exemptions below).
If a review still finds no mitigation available, then it is trivial to extend by changing the date and committing.
Therefore repeated exemptions of no more than a month or two are preferred over a single long exemption.

### Reviewing Expired Exemptions

When reviewing previously accepted risks, it would be prudent to examine how long the problem has existed.
If a problem is not fairly new, and still looks unlikely to be resolved, then reconsider both the _risk_ of exploitation and
the _cost_ of exploitation before deciding whether to continue extending an exemption.  It may now be
appropriate to consider alternatives.
