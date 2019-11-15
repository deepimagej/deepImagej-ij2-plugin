/*
 * DeepImageJ
 * 
 * https://deepimagej.github.io/deepimagej/
 *
 * Conditions of use: You are free to use this software for research or educational purposes. 
 * In addition, we expect you to include adequate citations and acknowledgments whenever you 
 * present or publish results that are based on it.
 * 
 * Reference: DeepImageJ: A user-friendly plugin to run deep learning models in ImageJ
 * E. Gomez-de-Mariscal, C. Garcia-Lopez-de-Haro, L. Donati, M. Unser, A. Munoz-Barrutia, D. Sage. 
 * Submitted 2019.
 *
 * Bioengineering and Aerospace Engineering Department, Universidad Carlos III de Madrid, Spain
 * Biomedical Imaging Group, Ecole polytechnique federale de Lausanne (EPFL), Switzerland
 *
 * Corresponding authors: mamunozb@ing.uc3m.es, daniel.sage@epfl.ch
 *
 */

/*
 * Copyright 2019. Universidad Carlos III, Madrid, Spain and EPFL, Lausanne, Switzerland.
 * 
 * This file is part of DeepImageJ.
 * 
 * DeepImageJ is free software: you can redistribute it and/or modify it under the terms of 
 * the GNU General Public License as published by the Free Software Foundation, either 
 * version 3 of the License, or (at your option) any later version.
 * 
 * DeepImageJ is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with DeepImageJ. 
 * If not, see <http://www.gnu.org/licenses/>.
 */

package deepimagej;

import org.tensorflow.Tensor;

import deepimagej.tools.ArrayOperations;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.imagej.tensorflow.Tensors;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;

public class TensorManagement {
	
	// From RAI to Tensor
	
	public static Tensor<?> imPlus2tensor(ImagePlus img, String form){
		// Convert ImagePlus into tensor calling the corresponding
		// method depending on the dimensions of the required tensor 
		// Find the number of dimensions of the tensor

		Img<FloatType> region = ImagePlusAdapter.wrap(img);
		int og_dims = region.numDimensions();
		// Increase the dimensions of the image in FIJI until they are the same as the target tensors dimensions
		String RAIForm = createRAIForm(og_dims);
		String expandedRAIForm = expandedForm(RAIForm, form);
		RandomAccessibleInterval<FloatType> feedable_image = adaptDimensions((RandomAccessibleInterval<FloatType>)region, form.length());
		int[] dimsAssociation = createDimOrder(expandedRAIForm.split(""), form);
		final Tensor<?> tensor = Tensors.tensor(feedable_image, dimsAssociation);
		return tensor;
	}
	
	
	public static String expandedForm(String original, String target) {
		String[] targetSeparatedForm = target.split("");		
		for (int i = 0; i < target.length(); i ++) {
			int index = original.indexOf(targetSeparatedForm[i]);
			if (index == -1) {
				// If the dimension is not found, locate it in the end. It 
				// does not matter if it makes sense or not because all of them
				// are going to be 1.
				original = original + targetSeparatedForm[i];
			}
		}
		return original;
	}
	
	
	public static RandomAccessibleInterval<FloatType> adaptDimensions(RandomAccessibleInterval<FloatType> region, int size){
		// These method adds the needed dimensions until the number of dimensions is needed matches the number
		// of dimensions of the variable size. The added dimension has always size 1. Ex: original dimensions
		//of region -->[256][256], original dimensions of size -->[-1][-1][-1]. Dimensions of
		// region after runing the method-->[256][256][1]
		int needed_rank = size;
		int current_rank = region.numDimensions();
		if (needed_rank > current_rank) {
			region = Views.stack(region);
			return adaptDimensions(region, size);
		}else {
			return region;
		}
	}
	
	public static String createRAIForm(int RAIRank) {
		String RAIForm = null;
		if (RAIRank == 2) {
			RAIForm = "HW";
		} else if (RAIRank == 3) {
			RAIForm = "HWC";
		}
		return RAIForm;
	}
	
	
	public static int[] createDimOrder(String[] originalOrder, String requiredOrder) {
		// Example: original_order = [c,d,e,b,a]; required_order = [d,e,b,c,a]
		// output--> dim_order = [3,0,1,2,4], because c goes in position 3, d in 0
		// position
		// and so on in the required_order array
		int size = originalOrder.length;
		int pos = 0;
		int[] dimOrder = new int[size];
		for (int i = 0; i < size; i++) {
			pos = requiredOrder.indexOf(originalOrder[i]);
			dimOrder[i] = pos;
		}
		return dimOrder;
	}
	
	
	// From Tensor to ImagePlus
	
	public static ImagePlus tensor2ImagePlus(Tensor<?> tensor, String form) {
		//Method to transform an ImagePlus into a TensorFLow tensor of the
		// dimensions specified by form
		String imPlusForm = createImPlusForm(form);
		int[] dimOrder = createDimOrder(imPlusForm.split(""), form);
		Img<FloatType> outputImage = Tensors.imgFloat((Tensor<Float>) tensor, dimOrder);
	    ImagePlus outputImage_ip = ImageJFunctions.wrap(outputImage, "final_img");
	    return outputImage_ip;
	}
	
	public static String createImPlusForm(String outputForm) {
		// The standard ImagePlus organization is "HWCNT". Consider N as Z because
		// it is going to be 1 always in this version
		String imPlusForm = null;
		int outputRank = outputForm.length();
		if (outputRank == 2) {
			imPlusForm = "HW";
		} else if (outputRank == 3) {
			if (outputForm.indexOf("N") != -1) {
				imPlusForm = "HWN";
			} else if (outputForm.indexOf("C") != -1) {
				imPlusForm = "HWC";
			} else if (outputForm.indexOf("Z") != -1) {
				imPlusForm = "HWZ";
			}
		}else if (outputRank == 4) {
			if (outputForm.indexOf("N") == -1) {
				imPlusForm = "HWCZ";
			} else if (outputForm.indexOf("C") == -1) {
				imPlusForm = "HWNZ";
			} else if (outputForm.indexOf("Z") == -1) {
				imPlusForm = "HWCN";
			}
		}else if (outputRank == 5) {
			imPlusForm = "HWCZN";
		}
		return imPlusForm;
	}
}