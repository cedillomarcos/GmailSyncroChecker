import javax.mail.Store;

import es.company.etc.subsystems.gmail.GmailGetterSyncro;


public class pruebagmail {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Store store;
		
	    GmailGetterSyncro gmailsyncro = new GmailGetterSyncro(null);
        gmailsyncro.connectViaPop3("email@","marcos","inbox");
        store = gmailsyncro.getStore();
	}

}
