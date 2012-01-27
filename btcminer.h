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
EP_CONFIG(2,0,BULK,OUT,512,2);	 

// select ZTEX USB FPGA Module 1.15 as target (required for FPGA configuration)
IDENTITY_UFM_1_15(10.0.1.1,0);	 
ENABLE_UFM_1_15X_DETECTION;

// enables high speed FPGA configuration, use EP 2
ENABLE_HS_FPGA_CONF(2);


// this product string is also used for identification by the host software
#define[PRODUCT_STRING]["btcminer for ZTEX FPGA Modules"]

#define[F_MIN_MULT][13]
#define[WATCHDOG_TIMEOUT][(300*100)]

#ifndef[F_M1]
#define[F_M1][800]
#endif


// !!!!! currently NUM_NONCES must not be larger than 2 !!!!!

__xdata BYTE run;

__xdata BYTE stopped;
__xdata WORD watchdog_cnt;

__xdata BYTE buf[NUM_NONCES*24];
__xdata BYTE buf_ptr1, buf_ptr2;

#define[PRE_FPGA_RESET][PRE_FPGA_RESET
    run = 0;
    CPUCS &= ~bmBIT1;	// stop clock
]

#define[POST_FPGA_CONFIG][POST_FPGA_CONFIG
    IOC = bmBIT2;	// reset PLL
    OEC = bmBIT0 | bmBIT1 | bmBIT2;
    IOC = bmBIT2;	// reset PLL
    stopped = 1;
    
    CPUCS |= bmBIT1;	// start clock
    
    OEA = bmBIT2 | bmBIT4 | bmBIT5 | bmBIT6 | bmBIT7;
    IOA = 0;
    OEB = 0;
    OED = 255;

    if ( is_ufm_1_15x ) {
	OEA |= bmBIT0;
	IOA0 = 1;
    }

    wait(100);
    
    set_freq(0);
    
    run = 1;
]

/* *********************************************************************
   ***** descriptor ****************************************************
   ********************************************************************* */
__code BYTE BitminerDescriptor[] = 
{   
    3,				// 0, version number
    NUM_NONCES*2-1,		// 1, number of nonces - 1
    (OFFS_NONCES+10000)&255,	// 2, ( nonce offset + 10000 ) & 255
    (OFFS_NONCES+10000)>>8,	// 3, ( nonce offset + 10000 ) >> 8
    F_M1 & 255,			// 4, frequency @ F_MULT=1 / 10kHz (LSB)
    F_M1 >> 8,			// 5, frequency @ F_MULT=1 / 10kHz (MSB)
    F_MULT-1,			// 6, frequency multiplier - 1 (default)
    F_MAX_MULT-1,		// 7, max frequency multiplier - 1 
    (HASHES_PER_CLOCK-1) & 255, // 8, (hashes_per_clck/128-1 ) & 266 
    (WORD)(HASHES_PER_CLOCK-1) >> 8,  // 9, (hashes_per_clck/128-1 ) >> 8 
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
    
    if ( f < F_MIN_MULT-1 )
	f = F_MIN_MULT-1;

    if ( f > F_MAX_MULT-1 )
	f = F_MAX_MULT-1;

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
    
    if ( stopped ) {
	IOC2 = 0;
	wait(200);
	stopped=0;
    }
    
    watchdog_cnt = 0;
    
    AUTOPTRL1=LO(&(buf));
    AUTOPTRH1=HI(&(buf));
    __asm
	push	ar2
  	mov	r2,#(NUM_NONCES*24);
        mov 	dptr,#_XAUTODAT1
        mov	a,#0
001$:
	movx 	@dptr,a
        djnz	r2, 001$
	
	pop	ar2
    __endasm;
    
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
// read date from FPGA
ADD_EP0_VENDOR_REQUEST((0x81,,
    watchdog_cnt = 0;
    MEM_COPY1(buf,EP0BUF,NUM_NONCES*24);
    EP0BCH = 0;
    EP0BCL = SETUPDAT[6];
,,
//  currently not supported
));;


/* *********************************************************************
   ***** EP0 vendor request 0x82 ***************************************
   ********************************************************************* */
// send descriptor
ADD_EP0_VENDOR_REQUEST((0x82,,
    MEM_COPY1(BitminerDescriptor,EP0BUF,64);
    EP0BCH = 0;
    EP0BCL = SETUPDAT[6];
,,
));;


/* *********************************************************************
   ***** EP0 vendor command 0x83 ***************************************
   ********************************************************************* */
// set frequency
ADD_EP0_VENDOR_COMMAND((0x83,,
    IOC2 = 1;
    set_freq(SETUPDAT[2]);
    wait(100);
    IOC2 = 0;
    stopped = 0;
    watchdog_cnt = 0;
,,
    NOP;
));; 


// include the main part of the firmware kit, define the descriptors, ...
#include[ztex.h]

#define[CP2][
    mov  a, _IOB	// 1
    movx @dptr,a
    setb _IOA6

    mov  a, _IOB	// 2
    movx @dptr,a
    clr _IOA6
]


void main(void)	
{
    BYTE b, c, p1, p2;
// init everything
    init_USB();

    buf_ptr1 = 0;
    buf_ptr2 = NUM_NONCES*12;

    AUTOPTRSETUP = 7;

    watchdog_cnt = 1;
    stopped = 1;
    run = 0;
    
    while (1) {	
    
	wait(10);

	if ( run ) {

	    EA = 0;	
	
	    b = 0;
	    do {

		IOA6 = 0;
		IOA7 = 1;	// write start signal
		IOA7 = 0;
	
		for ( c=0; c<NUM_NONCES*12; c+=12 ) {

		    p1 = c + buf_ptr1;
		    p2 = c + buf_ptr2;
	    	    
		    if ( ( buf[p1] != buf[p2] ) && ( buf[p2] == IOB ) ) {
			AUTOPTRL1=LO(&(buf[p2]));
			AUTOPTRH1=HI(&(buf[p2]));
			AUTOPTRL2=LO(&(buf[p1]));
			AUTOPTRH2=HI(&(buf[p1]));
			XAUTODAT1 = XAUTODAT2;
			XAUTODAT1 = XAUTODAT2;
			XAUTODAT1 = XAUTODAT2;
			XAUTODAT1 = XAUTODAT2;
		    
		    } 

	    	    AUTOPTRL1=LO(&(buf[p1]));
	    	    AUTOPTRH1=HI(&(buf[p1]));
    		    __asm
	    		mov dptr,#_XAUTODAT1
	    		CP2  // 2
	    		CP2  // 4
	    		CP2  // 6
	    		CP2  // 8
	    		CP2  // 10
	    		CP2  // 12
		    __endasm;
		}
	
	    b++;
	    } while (b<5 && (buf[buf_ptr1+NUM_NONCES*12-2] == buf[buf_ptr1+NUM_NONCES*12-1] ) );

	    EA = 1;	

	    b = buf_ptr2;
	    buf_ptr2 = buf_ptr1;
	    buf_ptr1 = b;

	    if ( is_ufm_1_15x )
		IOA0 = stopped ? 1 : 0;
	}
    
	watchdog_cnt += 1;
	if ( watchdog_cnt > WATCHDOG_TIMEOUT ) {
	    stopped = 1;
	    IOC2 = 1;
	}
	
	    
    }
}
