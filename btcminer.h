/*!
   btcminer -- BTCMiner for ZTEX USB-FPGA Modules: EZ-USB FX2 firmware
   Copyright (C) 2011 ZTEX GmbH
   http://www.ztex.de

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License version 3 as
   published by the Free Software Foundation.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
   General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, see http://www.gnu.org/licenses/.
!*/

#include[ztex-conf.h]	// Loads the configuration macros, see ztex-conf.h for the available macros
#include[ztex-utils.h]	// include basic functions

// configure endpoints 2 and 4, both belong to interface 0 (in/out are from the point of view of the host)
EP_CONFIG(2,0,BULK,OUT,512,4);	 

// select ZTEX USB FPGA Module 1.15 as target (required for FPGA configuration)
IDENTITY_UFM_1_15(10.0.1.1,0);	 

// enables high speed FPGA configuration, use EP 2
ENABLE_HS_FPGA_CONF(2);

// this product string is also used for identification by the host software
#define[PRODUCT_STRING]["btcminer for ZTEX FPGA Modules"]

xdata BYTE run;

#define[PRE_FPGA_RESET][PRE_FPGA_RESET
    run = 0;
]

#define[POST_FPGA_CONFIG][POST_FPGA_CONFIG
    OEA = bmBIT2 | bmBIT4 | bmBIT5 | bmBIT6 | bmBIT7;
    IOA = 0;
    OEB = 0;
    OEC = bmBIT0 | bmBIT1;
    IOC = 0;
    OED = 255;

    set_freq(0);	// start up with 8 MHz for safety
        
    run = 1;
]


/* *********************************************************************
   ***** descriptor ****************************************************
   ********************************************************************* */
__code BYTE BitminerDescriptor[] = 
{   
    2,				// 0, version number
    NUM_NONCES-1,		// 1, number of nonces - 1
    (OFFS_NONCES+10000)&255,	// 2, ( nonce offset + 10000 ) & 255
    (OFFS_NONCES+10000)>>8,	// 3, ( nonce offset + 10000 ) >> 8
    800 & 255,			// 4, frequency @ F_MULT=1 / 10kHz (LSB)
    800 >> 8,			// 5, frequency @ F_MULT=1 / 10kHz (MSB)
    F_MULT-1,			// 6, frequency multiplier - 1 (default)
    F_MAX_MULT-1,		// 7, max frequency multiplier - 1 
};
__code char bitfileString[] = BITFILE_STRING;
__code BYTE bitFileStringTerm = 0;



/* *********************************************************************
   ***** set_freq ******************************************************
   ********************************************************************* */
#define[PROGEN][IOA5]
#define[PROGCLK][IOA2]
#define[PROGDATA][IOA4]
void set_freq ( BYTE f ) {
    BYTE b,i;

    PROGEN = 1;

    PROGDATA = 1;
    PROGCLK = 1;
    PROGCLK = 0;

    PROGDATA = 0;
    PROGCLK = 1;
    PROGCLK = 0;
    
    b = 5;
    for ( i=0; i<8; i++ ) {
	PROGDATA = b & 1;
	PROGCLK = 1;
	PROGCLK = 0;
	b = b >> 1;
    }

    PROGEN = 0;
    
    PROGCLK = 1;
    PROGCLK = 0;
    PROGCLK = 1;
    PROGCLK = 0;
    PROGCLK = 1;
    PROGCLK = 0;
    
// load D
    if ( f > F_MAX_MULT-1 )
	f = F_MAX_MULT-1;
    PROGEN = 1;

    PROGDATA = 1;
    PROGCLK = 1;
    PROGCLK = 0;

    PROGCLK = 1;
    PROGCLK = 0;

    b = f;
    for ( i=0; i<8; i++ ) {
	PROGDATA = b & 1;
	PROGCLK = 1;
	PROGCLK = 0;
	b = b >> 1;
    }

    PROGEN = 0;
    
    PROGCLK = 1;
    PROGCLK = 0;
    PROGCLK = 1;
    PROGCLK = 0;
    PROGCLK = 1;
    PROGCLK = 0;

// GO
    PROGDATA = 0;
    
    PROGEN = 1;

    PROGCLK = 1;
    PROGCLK = 0;
    
    PROGEN = 0;

    PROGCLK = 1;
    PROGCLK = 0;
    PROGCLK = 1;
    PROGCLK = 0;
    PROGCLK = 1;
    PROGCLK = 0;
}    
   
/* *********************************************************************
   ***** EP0 vendor command 0x80 ***************************************
   ********************************************************************* */
// write data to FPGA
void ep0_write_data () {
    BYTE b;
    IOC0 = 1;    // reset on
    for ( b=0; b<EP0BCL; b++ ) {
	IOD = EP0BUF[b];
	IOC1 = !IOC1;
    }
    IOC0 = 0;    // reset off
}

ADD_EP0_VENDOR_COMMAND((0x80,,				
,,
    ep0_write_data();
));; 


/* *********************************************************************
   ***** EP0 vendor request 0x81 ***************************************
   ********************************************************************* */
void ep0_read_data () {
    BYTE b;
    for ( b=0; b<SETUPDAT[6]; b++ ) {
	EP0BUF[b] = IOB;
	IOA6 = !IOA6;
    }
    EP0BCH = 0;
    EP0BCL = SETUPDAT[6];
}

// read date from FPGA
ADD_EP0_VENDOR_REQUEST((0x81,,
    IOA7 = 1;	// write start signal
    IOA7 = 0;
    ep0_read_data ();
,,
    ep0_read_data ();
));;


/* *********************************************************************
   ***** EP0 vendor request 0x82 ***************************************
   ********************************************************************* */
void ep0_send_descriptor () {
    BYTE b = SETUPDAT[6];
    MEM_COPY1(BitminerDescriptor,EP0BUF,b);
    EP0BCH = 0;
    EP0BCL = b;
}   

// send descriptor
ADD_EP0_VENDOR_REQUEST((0x82,,
    ep0_send_descriptor();
,,
));;


/* *********************************************************************
   ***** EP0 vendor command 0x83 ***************************************
   ********************************************************************* */
// set frequency
ADD_EP0_VENDOR_COMMAND((0x83,,
    set_freq(SETUPDAT[2]);
,,
    NOP;
));; 


// include the main part of the firmware kit, define the descriptors, ...
#include[ztex.h]


void main(void)	
{
// init everything
    init_USB();

    while (1) {	
    }
}
