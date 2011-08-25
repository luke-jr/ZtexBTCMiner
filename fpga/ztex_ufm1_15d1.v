/*!
   btcminer -- BTCMiner for ZTEX USB-FPGA Modules: HDL code for ZTEX USB-FPGA Module 1.15b (one double hash pipe)
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

module ztex_ufm1_15d1 (fxclk_in, reset,  dcm_progclk, dcm_progdata, dcm_progen,  rd_clk, wr_clk, wr_start, read, write);

	input fxclk_in, reset, dcm_progclk, dcm_progdata, dcm_progen, rd_clk, wr_clk, wr_start;
	input [7:0] read;
	output [7:0] write;

	reg rd_clk_b1, wr_clk_b1, rd_clk_b2, wr_clk_b2, wr_start_buf, reset_buf;
	reg dcm_progclk_buf, dcm_progdata_buf, dcm_progen_buf;
	reg [351:0] inbuf;
	reg [95:0] outbuf;
	
	wire fxclk, clk;
	wire [31:0] golden_nonce, nonce2, hash2;
	
	miner128 m (
	    .clk(clk),
	    .reset(reset_buf),
	    .midstate(inbuf[351:96]),
	    .data(inbuf[95:0]),
	    .golden_nonce(golden_nonce),
	    .nonce2(nonce2),
	    .hash2(hash2)
	);

	BUFG bufg_fxclk (
          .I(fxclk_in),
          .O(fxclk)
        );

        DCM_CLKGEN #(
	  .CLKFX_DIVIDE(6.0),
          .CLKFX_MULTIPLY(20),
          .CLKFXDV_DIVIDE(2)
	) 
	dcm0 (
    	  .CLKIN(fxclk),
          .CLKFX(clk),
          .FREEZEDCM(1'b0),
          .PROGCLK(dcm_progclk_buf),
          .PROGDATA(dcm_progdata_buf),
          .PROGEN(dcm_progen_buf),
          .RST(1'b0)
	);

	assign write = outbuf[7:0];
	
	always @ (posedge clk)
	begin
    		if ( rd_clk_b1 != rd_clk_b2 ) 
		begin
		    inbuf[351:344] <= read;
		    inbuf[343:0] <= inbuf[351:8];
		end;
		    
		if (wr_start_buf)
		begin
   		    outbuf <= { hash2, nonce2, golden_nonce };
		end else begin
		    if ( wr_clk_b1 != wr_clk_b2 ) 
			outbuf[ 87 : 0 ] <= outbuf[ 95 : 8 ];
		end

		rd_clk_b1 <= rd_clk;
		rd_clk_b2 <= rd_clk_b1;
		wr_clk_b1 <= wr_clk;
		wr_clk_b2 <= wr_clk_b1;
		wr_start_buf <= wr_start;
		reset_buf <= reset;
		
		dcm_progclk_buf <= dcm_progclk;
		dcm_progdata_buf <= dcm_progdata;
		dcm_progen_buf <= dcm_progen;
	end

endmodule

