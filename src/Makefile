JC:= javac
JRUN:= java

JADEPATH:= ../lib/jade.jar;.

JLIBS:= -classpath $(JADEPATH)
JOPT := -encoding utf-8

HOST := localhost
PORT := 1099

INDIR := ../files/
NAMES := $(INDIR)names.txt
INPUTFILE := $(INDIR)input_file.txt
OUTPUTFILE := $(INDIR)output_file.txt

.PHONY: all run clean

all:
	make clean
	$(JC) $(JLIBS) *.java $(JOPT)

server: Main.class
	$(JRUN) $(JLIBS) Main $(NAMES)

client: Main.class
	$(JRUN) $(JLIBS) Main $(HOST) $(PORT) $(INPUTFILE) $(OUTPUTFILE)

clean:
	del *.class