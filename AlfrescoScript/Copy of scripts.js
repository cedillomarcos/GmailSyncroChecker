mySite = siteService.getSite("Demo");
mySiteNode = mySite.node;

logger.log(mySiteNode);


folder = mySiteNode.childByNamePath("documentLibrary");
logger.log(folder);
if(folder!=null){
  docLibNode = folder.childByNamePath("correos.entrantes");
  
   if (docLibNode == null) {
        docLibNode = folder.createNode("correos.entrantes", "cm:folder");
        if (docLibNode == null) {
            log("Unable to create container (correos.entrantes) for site (" + mySite.shortName +
                    "). (No write permission?)");
        } else {
            var docLibProps = new Array(1);
            docLibProps["{http://www.alfresco.org/model/emailserver/1.0}alias"] = mySite.shortName;
            docLibNode.addAspect("{http://www.alfresco.org/model/emailserver/1.0}aliasable", docLibProps);
            docLibNode.addAspect("{http://www.alfresco.org/model/system/1.0}undeletable");
            docLibNode.save();
          
            logger.log("Generada carpeta");
        }
    }
}