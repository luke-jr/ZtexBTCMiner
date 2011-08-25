/*!
   btcminer -- BTCMiner for ZTEX USB-FPGA Modules: HDL code: single hash miner
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

`define IDX(x) (((x)+1)*(32)-1):((x)*(32))

module miner66 (clk, reset,  midstate, data,  golden_nonce, nonce2, hash2);

	parameter NONCE_OFFS = 32'd0;
	parameter NONCE_INCR = 32'd1;

	input clk, reset;
	input [255:0] midstate;
	input [95:0] data;
	output reg [31:0] golden_nonce, nonce2, hash2;

	reg [31:0] nonce;
	wire [255:0] hash;
	
	reg [6:0] cnt = 7'd0;
	reg feedback = 1'b0;

	reg [255:0] state_buf;
	reg [511:0] data_buf;
	
	sha256_pipe65 p (
		.clk(clk),
		.state(state_buf),
		.data(data_buf),
		.hash(hash)
	);

	always @ (posedge clk)
	begin
		if ( cnt == 7'd65 )
		begin
		    feedback <= ~feedback;
		    cnt <= 7'd0;
		end else begin
		    cnt <= cnt + 7'd1;
		end

		if ( feedback ) 
		begin
		    data_buf <= { 256'h0000010000000000000000000000000000000000000000000000000080000000, 
		                  hash[`IDX(7)] + midstate[`IDX(7)], 
		                  hash[`IDX(6)] + midstate[`IDX(6)], 
		                  hash[`IDX(5)] + midstate[`IDX(5)], 
		                  hash[`IDX(4)] + midstate[`IDX(4)], 
		                  hash[`IDX(3)] + midstate[`IDX(3)], 
		                  hash[`IDX(2)] + midstate[`IDX(2)], 
		                  hash[`IDX(1)] + midstate[`IDX(1)], 
		                  hash[`IDX(0)] + midstate[`IDX(0)]
		                };
		    state_buf <= 256'h5be0cd191f83d9ab9b05688c510e527fa54ff53a3c6ef372bb67ae856a09e667;
		end else begin 
		    data_buf <= {384'h000002800000000000000000000000000000000000000000000000000000000000000000000000000000000080000000, nonce, data};
		    state_buf <= midstate;

		    hash2 <= hash[255:224];
		    nonce2 <= nonce;
		    
		    if ( reset )
		    begin
			nonce <= NONCE_OFFS;
			golden_nonce <= 32'd0;
		    end else begin
			nonce <= nonce + NONCE_INCR;
			if ( hash2 == 32'ha41f32e7 ) 
			begin
			    golden_nonce <= nonce2;
			end
		    end

		end
	end

endmodule
