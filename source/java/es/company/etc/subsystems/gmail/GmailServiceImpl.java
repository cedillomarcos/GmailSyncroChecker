package es.company.etc.subsystems.gmail;

import javax.mail.Session;

public class GmailServiceImpl implements GmailService {

	//Mantenemos una session de gmail en memoria para evitar
	//estar realizando conexiones continuamente
	Session session = null;
	
	@Override
	public boolean isAvailable() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getVersionString() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isSessionAvailable() {
		// TODO Auto-generated method stub
		return (session!=null) ? true : false;
	}

	@Override
	public Session getSession() {
		// TODO Auto-generated method stub
		return session;
	}

}
