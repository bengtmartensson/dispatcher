# NOTE: This Makefile is not required to build the program, for which maven
# is used. Instead, it invokes the program for tests and for transforming the
# output, for example to the lirc.xml file.

MYDIR := $(dir $(firstword $(MAKEFILE_LIST)))
TOP := $(realpath $(MYDIR))

include $(MYDIR)/common/makefiles/paths.mk

PROJECT_NAME := Dispatcher
PROJECT_NAME_LOWERCASE := $(shell echo $(PROJECT_NAME) | tr A-Z a-z)
EXTRACT_VERSION := $(TOP)/common/xslt/extract_project_version.xsl
VERSION := $(shell $(XSLTPROC) $(EXTRACT_VERSION) pom.xml)
PROJECT_JAR := target/$(PROJECT_NAME)-$(VERSION)-jar-with-dependencies.jar
PROJECT_BIN := target/$(PROJECT_NAME)-$(VERSION)-bin.zip
GH_PAGES := $(TOP)/gh-pages
ORIGINURL := $(shell git remote get-url origin)
INSTALLDIR := /usr/local/share/$(PROJECT_NAME_LOWERCASE)
BINLINK := /usr/local/bin/$(PROJECT_NAME_LOWERCASE)

default: $(PROJECT_JAR)

$(PROJECT_JAR) $(PROJECT_BIN):
	mvn install -Dmaven.test.skip=true

$(PROJECT_JAR)-test:
	mvn install -Dmaven.test.skip=false

release: push gh-pages tag deploy

version:
	@echo $(VERSION)

setversion:
	mvn versions:set -DnewVersion=$(NEWVERSION)
	git commit -S -m "Set version to $(NEWVERSION)" pom.xml

deploy:
	mvn deploy -P release

apidoc: target/site/apidocs
	$(BROWSE) $</index.html

javadoc: target/site/apidocs

target/site/apidocs:
	mvn javadoc:javadoc

push:
	git push

gh-pages: target/site/apidocs
	rm -rf $(GH_PAGES)
	git clone --depth 1 -b gh-pages ${ORIGINURL} ${GH_PAGES}
	( cd ${GH_PAGES} ; \
	cp -r ../target/site/apidocs/* . ; \
	git add * ; \
	git commit -a -m "Update of API documentation" ; \
	git push )

tag:
	git checkout master
	git status
	git tag -s -a Version-$(VERSION) -m "Tagging Version-$(VERSION)"
	git push origin Version-$(VERSION)

$(INSTALLDIR):
	mkdir -p $@

# Only for Unix-like systems
install: $(PROJECT_JAR) | $(INSTALLDIR)
	-rm -rf $(INSTALLDIR)/*
	cp -pr native $(INSTALLDIR)
	install -m 444 $(PROJECT_JAR) $(INSTALLDIR)
	install -m 444 src/main/config/dispatcher.service $(INSTALLDIR)
	install -m 555 src/main/config/dispatcher.sh	  $(INSTALLDIR)
	install -m 444 src/main/config/listener.xml	  $(INSTALLDIR)
	ln -sf $(INSTALLDIR)/$(PROJECT_NAME_LOWERCASE).sh $(BINLINK)
	ln -sf $(INSTALLDIR)/$(PROJECT_NAME_LOWERCASE).service /etc/systemd/system

uninstall:
	rm -rf $(INSTALLDIR)
	rm $(BINLINK)

clean:
	mvn clean
	rm -rf $(GH_PAGES) pom.xml.versionsBackup

.PHONY: clean $(PROJECT_JAR)-test release
