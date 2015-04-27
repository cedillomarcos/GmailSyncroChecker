package es.company.etc.subsystems.gmail;

import javax.mail.Session;

public interface GmailService {
	
	public abstract boolean isAvailable();
	public abstract String getVersionString();
	
	public abstract boolean isSessionAvailable();
	public abstract Session getSession();	
}
