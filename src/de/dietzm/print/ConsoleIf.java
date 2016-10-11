package de.dietzm.print;

import de.dietzm.Temperature;

public interface ConsoleIf {
	public void appendText(CharSequence ... txt);
	public void appendTextNoCR(CharSequence ... txt);
	public void setTemp(Temperature temp);
	public void clearConsole();	
	public int chooseDialog(final String[] items,final String[] values, int type);
	public void setWakeLock(boolean active);
	public void setPrinting(boolean printing);
	public void log(String tag, String value, ReceiveBuffer buf );
	public void log(String tag, String value );
	public boolean hasWakeLock();
	public void updateState(int statemsg,CharSequence detail,int progressPercent);
	public void updateState(int msgtype,int msgnr,int progressPercent);
}
