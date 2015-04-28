package es.company.etc.subsystems.gmail;

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.mail.pop3.POP3SSLStore;

import es.company.etc.repo.gmail.EmailSiteClassifier;


/**
 * Realiza una conexion contra gmail y recupera los mensajes de la carpeta INBOX
 * realizando un reenvio al servicio local de alfresco y teneniendo en cuenta la 
 * lista de sites que admiten la recepcion del correo. 
 * 
 */

public class GmailGetterSyncro {

	private static Log logger = LogFactory.getLog(EmailSiteClassifier.class);
	 
	String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
	Properties props = null;
	Session session = null;
	Store store = null;
	Folder folder = null;
	List<Message> rejectedEmail = null;
	List<String> aliases = null;
	
	/**
	 * Constructor
	 */
	public GmailGetterSyncro(List<String> aliases){
		 props = System.getProperties();
		 this.aliases = aliases;
		 this.rejectedEmail = new ArrayList<Message>();
	}
	
	
	/**
	 * 
	 */
	public void connectViaPop3(String username, String password, String folderbox){
		   	        
		props.setProperty("mail.pop3.socketFactory.class", SSL_FACTORY);
		props.setProperty("mail.pop3.socketFactory.fallback", "false");
		props.setProperty("mail.pop3.port",  "995");
		props.setProperty("mail.pop3.socketFactory.port", "995");
	  
		try {
			    URLName url = new URLName("pop3", 
			       						  "pop.gmail.com", 995, "",
			       						  username, 
			       						  password);
			    session = Session.getInstance(props, null);
			        
			    if(store==null || !store.isConnected()){
			        store = new POP3SSLStore(session, url);
			        store.connect();
				}    
		        
		        try {
					openFolder(folderbox);
					sendLocalMensage(getMessages());
					closeFolder();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
   	        
	       
	        
	      } catch (javax.mail.NoSuchProviderException e) {
	          System.out.println(e.toString());
	      } catch (MessagingException e) {
	          System.out.println(e.toString());
	      }    
	}
	
	
	 public void openFolder(String folderName) throws Exception {
	        // Open the Folder
	        folder = store.getDefaultFolder();
	        folder = folder.getFolder(folderName);
	        if (folder == null) {
	            throw new Exception("Invalid folder");
	        }

	        // try to open read/write and if that fails try read-only
	        try {
	            folder.open(Folder.READ_WRITE);
	        } catch (MessagingException ex) {
	            folder.open(Folder.READ_ONLY);
	        }
	    }
	    
	 
	public void closeFolder() throws Exception {
	      folder.close(false);
	}
	 
	 
	public Message[] getMessages() throws MessagingException{
		Message[] msgs = folder.getMessages();
        folder.getUnreadMessageCount();
        // Use a suitable FetchProfile
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.FLAGS);
        folder.setFlags(msgs, new Flags(Flags.Flag.SEEN), true);
        folder.fetch(msgs, fp);
        
        return msgs;
	}
	 
	 
	/**
	 * 
	 */
	public void connectViaImap(){
	      props.setProperty("mail.store.protocol", "imaps");
	      
	      try {
	              session = Session.getDefaultInstance(props, null);
	              store = session.getStore("imaps");

	              // IMAP host for gmail. 
	              // Replace <username> with the valid username of your Email ID.
	              // Replace <password> with a valid password of your Email ID.
	              //new IMAPSSLStore()
	              store.connect("imap.gmail.com", "email", "pass");

	              // IMAP host for yahoo.
	              //store.connect("imap.mail.yahoo.com", "<username>", "<password>");

	              System.out.println(store);

	              Folder inbox = store.getFolder("Inbox");
	              inbox.open(Folder.READ_ONLY);
	              
	              try {
	            	  FlagTerm ft = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
	                  Message msg[] = inbox.search(ft);
	                  System.out.println("MAILS: "+msg.length);
	                  
	                  sendLocalMensage(msg);
	                  
	              } catch (MessagingException e) {
	                  System.out.println(e.toString());
	              } catch (Exception e) {
	  				// TODO Auto-generated catch block
	  				e.printStackTrace();
	  			  }
	           
	              
	      } catch (javax.mail.NoSuchProviderException e) {
	          System.out.println(e.toString());
	      } catch (MessagingException e) {
	          System.out.println(e.toString());
	      }
	}
  
 	
	 /**
	  * Envia un correo al puerto local smtp de Alfresco como inbound email
	  * @param msg
	  * @throws Exception
	  */
	 private void sendLocalMensage(Message[] msg) throws Exception {

	        // Get system properties
		    Properties properties = System.getProperties();
		    properties.setProperty("mail.smtp.host", "127.0.0.1");
			Session localsession = Session.getDefaultInstance(properties);
			
			Pattern p = Pattern.compile("\\[(.*)\\]");
			
			for (Message message : msg) {
				try {
					
					//Comprobamos si el mensaje esta en la lista de emails de alfresco
					//Para ello obtenemos del mensaje el subject que tiene que tener el codigo de proyecto con el formato [ID_PROYECTO]
					String subject = message.getSubject();
					Matcher m = p.matcher(subject);
					if(m.find()==true && m.groupCount()==1){
						
						String toCheck = m.group().replace("[","").replace("]", "");
						if(aliases.contains(toCheck)){
							MimeMessage message2 = new MimeMessage(localsession, message.getInputStream());
							
							Enumeration<Header> headers = message.getAllHeaders();
							while(headers.hasMoreElements()){
								Header header = (Header)headers.nextElement();
								message2.addHeader(header.getName(), header.getValue());
							}
							message2.setContent(message.getContent(),message.getContentType());
							message2.setDataHandler(message.getDataHandler());
							message2.setDescription(message.getDescription());
							message2.setDisposition(message.getDisposition());
							
							String subjNew = subject.replace("[" + toCheck + "]", "");
							message2.setSubject(subjNew);
							
							message2.setRecipient(Message.RecipientType.TO,
				                    			  new InternetAddress( toCheck + "@company.es"));
							
							Transport.send(message2);
					   }else{
						   createRejectedEmail(message,
								   			   "Rechazado, no existe ningun proyecto con el ID indicado en el asunto del correo");
					   }
					}else{
						createRejectedEmail(message,
									 	  "Rechazado por no tener [ID_PROYECTO] en el asunto del correo");
					}
				
				} catch (Exception e) {
					createRejectedEmail(message,
					 				    "Rechazado por:" + e.getMessage());
					logger.trace("Error" + e);
				}
			}	
	    }
	 
	 
	 /**
	  * Create a rejected email
	  * @param message
	  * @param motivo
	  * @return
	  * @throws Exception
	  */
	 private void createRejectedEmail (Message message,String motivo) throws Exception{

			MimeMessage message2 = new MimeMessage((MimeMessage)message);
			Enumeration<Header> headers = message.getAllHeaders();
			while(headers.hasMoreElements()){
				Header header = (Header)headers.nextElement();
				message2.addHeader(header.getName(), header.getValue());
			}
			message2.setContent(message.getContent(),message.getContentType());
			message2.setDataHandler(message.getDataHandler());
			message2.setDescription(message.getDescription());
			message2.setDisposition(message.getDisposition());	
			message2.setSubject("Rechazado: " + message.getSubject());
			message2.setSentDate(new Date());
			Address[] addressfrom = message2.getFrom();
			message2.setRecipient(Message.RecipientType.TO, 
											addressfrom[0]);
	        // Set message content
			message2.setText(motivo);
			
			rejectedEmail.add(message2);
			
	 }


	public Store getStore() {
		return store;
	}


	public void setStore(Store store) {
		this.store = store;
	}


	public List<Message> getRejectedEmail() {
		return rejectedEmail;
	}
	 
}
