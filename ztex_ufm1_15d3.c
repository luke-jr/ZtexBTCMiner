/*!
   btcminer -- BTCMiner for ZTEX USB-FPGA Modules: EZ-USB FX2 firmware for ZTEX USB FPGA Module 1.15d (one double hash pipe)
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

#define[NUM_NONCES][1]
#define[OFFS_NONCES][0]
#define[F_MULT][33]
//#define[F_MAX_MULT][36]
#define[F_MAX_MULT][40]
#define[HASHES_PER_CLOCK][128]
#define[BITFILE_STRING]["ztex_ufm1_15d3"]

#define[F_M1][600]
#define[F_DIV][4]
#define[F_MIN_MULT][17]

#include[btcminer.h]
