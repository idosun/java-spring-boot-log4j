# Must have `sentry-cli` installed globally
# Following variable must be passed in
#  SENTRY_AUTH_TOKEN

SENTRY_ORG=testorg-az
SENTRY_PROJECT=hardware-store-spring-log4j2
VERSION=`sentry-cli releases propose-version`

deploy: setup_release run_jar

setup_release: create_release associate_commits

create_release:
	sentry-cli releases -o $(SENTRY_ORG) new -p $(SENTRY_PROJECT) $(VERSION)

associate_commits:
	sentry-cli releases -o $(SENTRY_ORG) -p $(SENTRY_PROJECT) set-commits --auto $(VERSION)
	
#deploy_release:
#	sentry-cli releases -o $(SENTRY_ORG) deploys $(VERSION) new -e $(ENVIRONMENT)

run_jar:
	mvn clean package && \
	 java -agentpath:libsentry_agent.dylib -Dsentry.release=$(VERSION) -Dsentry.properties.file=sentry-production.properties -jar target/sentry-spring-boot-log4j2-example-0.0.1.jar
