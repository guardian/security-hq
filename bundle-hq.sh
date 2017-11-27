#!/usr/bin/env bash

# utils
green=`tput setaf 2`
reset=`tput sgr0`

# set CWD to root of repo
cd $(dirname $(realpath $0))

# ask sbt/play to package up the security-hq application
#sbt "project hq" dist

# create elastic beanstalk bundle based on the zip produced by `zip`
cd hq/target/universal

tmpbundledir=`mktemp -d 2>/dev/null || mktemp -d -t 'tmpbundle'`

cp security-hq.zip $tmpbundledir/
cd $tmpbundledir

unzip security-hq.zip
rm security-hq.zip
# move contents of beanstalk dir to the root of the package
cp security-hq/beanstalk/* .

zip -r security-hq.zip * --exclude security-hq/share security-hq/beanstalk

cd -
mv $tmpbundledir/security-hq.zip .

# clean up temp dir
rm -r $tmpbundledir

echo "${green}Created Elastic Beanstalk bundle at ${PWD}/security-hq.zip${reset}"
