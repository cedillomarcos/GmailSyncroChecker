package es.company.etc.repo.gmail;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.mail.Message;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.lock.JobLockService.JobLockRefreshCallback;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.security.authentication.AuthenticationContext;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.ResultSetRow;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.VmShutdownListener.VmShutdownException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import es.company.etc.subsystems.gmail.GmailGetterSyncro;

/**
 *  Servicio que se encarga de buscar dentro de los sitios aquellas espacios que contenga
 *  para almacenar los correos entrantes.
 * 
 *  Una vez recuperada la lista de correos se almacena y se procesa la lista de correos
 *  de gmail, redirigiendo el correo a su carpeta especifica en base al asunto, que vendra
 *  el codigo de proyecto con el formato [ID_PROYECTO]
 * 
 *  En el caso que ocurriese un error a la hora de clasificar el correo, este sera devuelto
 *  al remitente del correo con un texto explicativo del problema de la clasificacion
 *  
 *  Para evitar que se superponga procesos el sistema bloquea el job de execucion
 *  
 */
public class EmailSiteClassifier {

	 private static Log logger = LogFactory.getLog(EmailSiteClassifier.class);
	 
	 
	 /** The name of the lock used to ensure that feed generator does not run on more than one node at the same time */
	 private static final QName LOCK_QNAME = QName.createQName(NamespaceService.SYSTEM_MODEL_1_0_URI, "EmailSiteClassifier");
	    
	 /** The time this lock will persist in the database (30 sec but refreshed at regular intervals) */
	 private static final long LOCK_TTL = 1000 * 30;
	 

	 private AuthenticationService authenticationService;
	 private TransactionService transactionService;
	 private JobLockService jobLockService;
	 private AuthenticationContext authenticationContext;
	 private JavaMailSender emailService;
	 
	 private NodeService nodeService;
	 private SearchService searchService;
	 
	 
	 /** Authentication credentials */
	 private String username;
	 private String password;
	 private String inbox;
	 private String protocol;
	 private LockTracker lockTracker = new LockTracker();
	 private Store store;
	 
	 public void execute()
	    {
	        checkProperties();
	        
	        // Bypass if the system is in read-only mode
	        if (transactionService.isReadOnly())
	        {
	            if (logger.isDebugEnabled())
	            {
	                logger.debug("Gmail connect bypassed; the system is read-only");
	            }
	            return;
	        }
	        
	        
	        try
	        {
	        	acquireLock();
	        	
	            if (logger.isTraceEnabled())
	            {
	                logger.trace("EmailSiteClassifier started");
	            }
	            
	            executeInternal();
	            
	            // Done
	            if (logger.isTraceEnabled())
	            {
	                logger.trace("EmailSiteClassifier completed");
	            }
	        }
	        catch (LockAcquisitionException e)
	        {
	            // Job being done by another process
	            if (logger.isDebugEnabled())
	            {
	                logger.debug("EmailSiteClassifier already underway");
	            }
	        }
	        catch (VmShutdownException e)
	        {
	            // Aborted
	            if (logger.isDebugEnabled())
	            {
	                logger.debug("EmailSiteClassifier aborted");
	            }
	        }
	        finally
	        {
	        	releaseLock();
	        }
	    }	

	 
	 /**
	  * Execute process
	  * 
	  */
	 private void executeInternal(){
		    List<String> aliasEmail = new ArrayList<String>();
		 
	        SearchParameters sp = new SearchParameters();
	        StoreRef storeRef = new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "SpacesStore");
	        sp.addStore(storeRef);
	        sp.setLanguage(SearchService.LANGUAGE_LUCENE);
	        //Busqueda dentro de sites de las carpetas con el aspecto alias de entrada de correos
	        sp.setQuery("+PATH:\"/app:company_home/st:sites//*\" +@emailserver\\:alias:*");
	      
	        
	        //sp.setQuery("TYPE:\"{http://www.alfresco.org/model/content/1.0}content\"");
	        ResultSet results = null;
	        try
	        {
	        	authenticationContext.setSystemUserAsCurrentUser();
	            results = searchService.query(sp);
	            for(ResultSetRow row : results) { 
	            	Serializable aliasqname = row.getValues().get("{http://www.alfresco.org/model/emailserver/1.0}alias");
	            	aliasEmail.add(aliasqname.toString());
	            } 
	        } catch (Exception e) {
	        	 logger.error("Error execute query: " + e);
			}  finally  {
				if(results != null)	{ 
					results.close(); 
				}
			}
			
	        //Conecta contra gmail y realiza la asignacion de los correos encontrados en la carpeta
	        //indicada y que no hayan sido leidos.
	        GmailGetterSyncro gmailsyncro = new GmailGetterSyncro(aliasEmail);
	        gmailsyncro.setStore(store);
	        gmailsyncro.connectViaPop3(username,password,inbox);
	        store = gmailsyncro.getStore();
	        
	        for (Message rejectmessage : gmailsyncro.getRejectedEmail()) {
		        emailService.send((MimeMessage)rejectmessage);	
			}
	 }
	 
	 
	   /**
	     * Perform basic checks to ensure that the necessary dependencies were injected.
	     */
	   private void checkProperties()
	    {
	        PropertyCheck.mandatory(this, "nodeService", nodeService);
	        PropertyCheck.mandatory(this, "searchService", searchService);
	        PropertyCheck.mandatory(this, "transactionService", transactionService);
	        PropertyCheck.mandatory(this, "authenticationContext", authenticationContext);
	    }
	 
	    
	   private void acquireLock() throws LockAcquisitionException
	    {
	        // Try to get lock
	        String lockToken = jobLockService.getLock(LOCK_QNAME, LOCK_TTL);

	        // Got the lock - now register the refresh callback which will keep the lock alive.
	        this.lockTracker.refreshLock(lockToken);

	        if (logger.isDebugEnabled())
	        {
	            logger.debug("lock aquired:  " + lockToken);
	        }
	    }
	    
	    private void releaseLock()
	    {
	        lockTracker.releaseLock();
	    }


	    private class LockTracker implements JobLockRefreshCallback
	    {
	        private String lockToken = null;

	        void refreshLock(String lockToken)
	        {
	            if(this.lockToken != null)
	            {
	                throw new IllegalStateException("lockToken is not null");
	            }
	            this.lockToken = lockToken;
	            jobLockService.refreshLock(lockToken, LOCK_QNAME, LOCK_TTL, this);
	        }

	        void releaseLock()
	        {
	            if(isActive())
	            {
	                jobLockService.releaseLock(lockToken, LOCK_QNAME);
	                lockToken = null;
	                if (logger.isInfoEnabled())
	                {
	                    logger.info("Lock released: " + LOCK_QNAME + ", lock token " + lockToken);
	                }
	            }
	        }

	        @Override
	        public boolean isActive()
	        {
	            return (lockToken != null);
	        }

	        @Override
	        public void lockReleased()
	        {
	            // note: currently the cycle will try to complete (even if refresh failed)
	            if (logger.isInfoEnabled())
	            {
	                logger.info("Lock released (refresh failed): " + LOCK_QNAME + ", lock token " + lockToken);
	            }
	            lockToken = null;
	        }
	    }
	 
	    
	    public AuthenticationService getAuthenticationService() {
			return authenticationService;
		}


		public void setAuthenticationService(AuthenticationService authenticationService) {
			this.authenticationService = authenticationService;
		}


		public TransactionService getTransactionService() {
			return transactionService;
		}


		public void setTransactionService(TransactionService transactionService) {
			this.transactionService = transactionService;
		}


		public JobLockService getJobLockService() {
			return jobLockService;
		}


		public void setJobLockService(JobLockService jobLockService) {
			this.jobLockService = jobLockService;
		}


		public NodeService getNodeService() {
			return nodeService;
		}


		public void setNodeService(NodeService nodeService) {
			this.nodeService = nodeService;
		}


		public SearchService getSearchService() {
			return searchService;
		}


		public void setSearchService(SearchService searchService) {
			this.searchService = searchService;
		}


		public LockTracker getLockTracker() {
			return lockTracker;
		}


		public void setLockTracker(LockTracker lockTracker) {
			this.lockTracker = lockTracker;
		}


		public AuthenticationContext getAuthenticationContext() {
			return authenticationContext;
		}


		public void setAuthenticationContext(AuthenticationContext authenticationContext) {
			this.authenticationContext = authenticationContext;
		}


		public String getUsername() {
			return username;
		}


		public void setUsername(String username) {
			this.username = username;
		}


		public String getPassword() {
			return password;
		}


		public void setPassword(String password) {
			this.password = password;
		}


		public String getInbox() {
			return inbox;
		}


		public void setInbox(String inbox) {
			this.inbox = inbox;
		}


		public String getProtocol() {
			return protocol;
		}


		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}


		public JavaMailSender getEmailService() {
			return emailService;
		}


		public void setEmailService(JavaMailSender emailService) {
			this.emailService = emailService;
		}


}
