#########################
# configuration section #
#########################

# Defines the location of the EZ-USB SDK
ZTEXPREFIX=../../ztex

# The name of the jar archive
JARTARGET=ZtexBTCMiner.jar
# Java Classes that have to be build 
CLASSTARGETS=BTCMiner.class
# Extra dependencies for Java Classes
CLASSEXTRADEPS=

# ihx files (firmware ROM files) that have to be build 
IHXTARGETS=ztex_ufm1_15d1.ihx ztex_ufm1_15b.ihx
# Extra Dependencies for ihx files
IHXEXTRADEPS=btcminer.h

# Extra files that should be included into th jar archive
EXTRAJARFLAGS=
EXTRAJARFILES=ztex_ufm1_15b.ihx ztex_ufm1_15d1.ihx fpga/ztex_ufm1_15b.bit fpga/ztex_ufm1_15d1.bit
# fpga/ztex_ufm1_15d.bit

################################
# DO NOT CHANAGE THE FOLLOWING #
################################
# includes the main Makefile
include $(ZTEXPREFIX)/Makefile.mk
