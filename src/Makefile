JC:= javac
JRUN:= java

JADEPATH:= ../lib/jade.jar;.

JLIBS:= -classpath $(JADEPATH)
JOPT := -encoding utf-8

HOST := 192.168.56.1

INDIR := ../files/
NAMES := $(INDIR)names.txt
INPUTFILE := $(INDIR)input_file.txt
OUTPUTFILE := $(INDIR)output_file.txt

.PHONY: all run clean

all:
	make clean
	$(JC) $(JLIBS) *.java $(JOPT)

run_server: Main.class
	$(JRUN) $(JLIBS) Main $(NAMES)

run_client: Main.class
	$(JRUN) $(JLIBS) Main $(HOST) $(INPUTFILE) $(OUTPUTFILE)

clean:
	del *.class