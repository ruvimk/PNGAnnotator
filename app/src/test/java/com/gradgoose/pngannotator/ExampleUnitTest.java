package com.gradgoose.pngannotator;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
	@Test
	public void addition_isCorrect () throws Exception { 
		assertEquals (4, 2 + 2); 
	} 
	
	@Test public void intersection_isCorrect () throws Exception { 
		float sm = PngEdit.findTwoLineSegmentIntersectionArcLengthPosition (
				-3, 0, -6, 1, 
				4, 0, 7, 2 
		); 
		assertEquals ((int) (100 * sm), (int) (100 * -4.919)); 
	} 
}