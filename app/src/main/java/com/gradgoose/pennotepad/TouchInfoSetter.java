package com.gradgoose.pennotepad;

public interface TouchInfoSetter { 
	void setLastTouchedPoint (float x, float y); 
	float getLastTouchedX (); 
	float getLastTouchedY (); 
	void setLastTouchedToolType (int type); 
	int getLastTouchedToolType (); 
} 
