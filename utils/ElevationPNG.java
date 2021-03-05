/* GeoImageViewer - Image Viewer linked to Maps
   Version 0.1 for HTML-browsers

   Copyright (C) 2021 - Helmut Dersch  der@hs-furtwangen.de
   
   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2, or (at your option)
   any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.  */

/* Convert SRTM-tiles to PNG-images:
*    Get tiles from https://dds.cr.usgs.gov/srtm/version2_1/
+        or derived tiles from other sources, e.g. tile N47E007.hgt.zip
*    Convert to png image with this command:
*       java ElevationPNG N47E007.hgt.zip
*    To reduce filesize: Open and resave as png with highest compression level 
*    using The Gimp or other graphics programs.
*/

import java.util.*;
import java.io.*;
import java.awt.image.*;

public class ElevationPNG{

	// interpolate horiz. and vertic., then average
        static void fillHoles(short el[], int w, int h ){
                short elc[] = new short[w * h], el1, el2;
                System.arraycopy(el, 0, elc, 0, w * h);
		for(int y = 0; y < h; y++) 
                        for(int x = 0; x < w; x++) {
                                if (el[y * w + x] != Short.MIN_VALUE)
                                        continue;
                                int xe = x + 1;
                                while(xe < w && el[y * w + xe] == Short.MIN_VALUE)
                                        xe++;
                                if(x == 0 && xe == w) { // set to 0
                                        el1 = el2 = 0;
                                } else {
                                        el1 = (x == 0 ? el[y * w + xe] : el[y * w + x - 1]);
                                        el2 = (xe >= w ? el1 : el[y * w + xe]);
                                }
                                double de = (el2 - el1) / (double)(xe + 1 - x);
                                for(int j = 0; j < (xe - x); j++)
                                        el[y * w + x + j] = (short) (el1 + (short) Math.round((j + 1) * de));
                                x = xe;
                        }
                        
                for(int x = 0; x < w; x++) 
                        for(int y = 0; y < h; y++){
                                if (elc[y * w + x] != Short.MIN_VALUE)
                                        continue;
                                int ye = y + 1;
                                while(ye < h && elc[ye * w + x] == Short.MIN_VALUE)
                                        ye++;
                                if(y == 0 && ye == h) {
                                        el1 = el2 = 0;
                                } else {
                                        el1 = (y == 0 ? elc[ye * w + x] : elc[(y - 1) * w + x]);
                                        el2 = (ye >= h ? el1 : elc[ye * w + x]);
                                }
                                double de = (el2 - el1) / (double)(ye + 1 - y);
                                for(int j = 0; j < (ye - y); j++)
                                        elc[(y + j) * w + x] = (short) (el1 + (short) Math.round((j + 1) * de));
                                y = ye;
                        }
                        
                for(int k = 0; k < w * h; k++) {
                        el[k] = (short)((el[k] + elc[k] + 1) / 2);
                }
	}
	
	// convert 2 bytes to signed short
	static int b2s(byte a, byte b) {
                int A = (a >= 0? a : 256 + a);
		int B = (b >= 0? b : 256 + b);
		int r = 256 * A + B; // unsigned short, bigendian
		return r > Short.MAX_VALUE ? r - 256 * 256 : r;
        }
        
        // convert byte buffer to shorts
	static short[] conv2Shorts(byte buf[], int len) {
                short el[] = new short[len / 2];
                for(int k = 0; k < len / 2; k++)
                        el[k] = (short)b2s(buf[2 * k], buf[2 * k + 1]);
                return el;
        }

        public static void usage() {
                System.err.println("Usage: java ElevationPNG infile");
                System.exit(0);
        }
        

        public static void main(String args[]) {
                 if(args.length < 1)
                        usage();
                
                int BUFSIZE = 100000;
                try{
                        // read input file into byte buffer
                        InputStream fin = new FileInputStream(args[0]);
                        if(args[0].toLowerCase().endsWith(".zip")){
                                java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(fin);
                                z.getNextEntry(); 
                                fin = z;
                        }
                        byte hgt[] = new byte[BUFSIZE];
                        byte buf[] = new byte[BUFSIZE];
                        
                        int len, off = 0;
                        while((len = fin.read(buf)) > 0) {
                                if(off + len > hgt.length) {
                                        byte hn[] = new byte[hgt.length + BUFSIZE];
                                        System.arraycopy(hgt, 0, hn, 0, off);
                                        hgt = hn;
                                }
                                System.arraycopy(buf, 0, hgt, off, len);
                                off += len;
                        }
                        fin.close();
                        
                        // convert bytes to shorts
                        short el[] = conv2Shorts(hgt, off);
                        
                        int w = (int)Math.round(Math.sqrt(el.length));
                        
                        // interpolate holes
                        fillHoles(el, w, w);
                        
                        // create encoded image and save
                        int idata[] = new int[w * w];
                        for(int y = 0; y < w; y++)
                                for(int x = 0; x < w; x++) {
                                        int idx = y * w + x;
                                        short ele = el[idx];
                                        idata[idx] = ele << 8; // red & green channels
                                }
                        
                        BufferedImage img = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB); 
                        img.setRGB(0, 0, w, w, idata, 0, w);
                        String fname = args[0];
                        if(fname.indexOf('/') >= 0)
                                fname = fname.substring(fname.lastIndexOf('/') + 1);
                        if(fname.endsWith(".zip"))
                                fname = fname.substring(0, fname.length() - 4);
                        if(fname.endsWith(".hgt"))
                                fname = fname.substring(0, fname.length() - 4);
                        fname = fname + ".png";
                        javax.imageio.ImageIO.write(img, "png", new File(fname));
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }
}
                                        
                        
