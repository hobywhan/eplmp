/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/
package org.polarsys.eplmp.server.dao;

import org.polarsys.eplmp.core.common.BinaryResource;
import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.document.*;
import org.polarsys.eplmp.core.exceptions.CreationException;
import org.polarsys.eplmp.core.exceptions.DocumentIterationNotFoundException;
import org.polarsys.eplmp.core.exceptions.DocumentRevisionAlreadyExistsException;
import org.polarsys.eplmp.core.exceptions.DocumentRevisionNotFoundException;
import org.polarsys.eplmp.core.meta.Tag;
import org.polarsys.eplmp.core.workflow.Workflow;

import javax.persistence.*;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocumentRevisionDAO {

    private EntityManager em;
    private Locale mLocale;
    private static final int MAX_RESULTS = 500;
    private static final Logger LOGGER = Logger.getLogger("DocumentRevisionDAO");

    public DocumentRevisionDAO(Locale pLocale, EntityManager pEM) {
        em = pEM;
        mLocale = pLocale;
    }

    public DocumentRevisionDAO(EntityManager pEM) {
        em = pEM;
        mLocale = Locale.getDefault();
    }

    public String findLatestDocMId(String pWorkspaceId, String pType) {
        String docMId;
        Query query = em.createQuery("SELECT m.id FROM DocumentMaster m "
                + "WHERE m.workspace.id = :workspaceId "
                + "AND m.type = :type "
                + "AND m.creationDate = ("
                + "SELECT MAX(m2.creationDate) FROM DocumentMaster m2 "
                + "WHERE m2.workspace.id = :workspaceId "
                + "AND m2.type = :type"
                + ")");
        query.setParameter("workspaceId", pWorkspaceId);
        query.setParameter("type", pType);
        docMId = (String) query.getSingleResult();
        return docMId;
    }

    public List<DocumentRevision> findDocRsByFolder(String pCompletePath) {
        TypedQuery<DocumentRevision> query = em.createQuery("SELECT DISTINCT d FROM DocumentRevision d WHERE d.location.completePath = :completePath", DocumentRevision.class);
        query.setParameter("completePath", pCompletePath);
        return query.getResultList();
    }

    public List<DocumentRevision> findDocRsByTag(Tag pTag) {
        TypedQuery<DocumentRevision> query = em.createQuery("SELECT DISTINCT d FROM DocumentRevision d WHERE :tag MEMBER OF d.tags", DocumentRevision.class);
        query.setParameter("tag", pTag);
        return query.getResultList();
    }

    public List<DocumentRevision> findCheckedOutDocRs(User pUser) {
        TypedQuery<DocumentRevision> query = em.createQuery("SELECT DISTINCT d FROM DocumentRevision d WHERE d.checkOutUser = :user", DocumentRevision.class);
        query.setParameter("user", pUser);
        return query.getResultList();
    }

    public DocumentRevision loadDocR(DocumentRevisionKey pKey) throws DocumentRevisionNotFoundException {
        DocumentRevision docR = em.find(DocumentRevision.class, pKey);
        if (docR == null) {
            throw new DocumentRevisionNotFoundException(mLocale, pKey);
        } else {
            return docR;
        }
    }

    public DocumentIteration loadDocI(DocumentIterationKey pKey) throws DocumentIterationNotFoundException {
        DocumentIteration docI = em.find(DocumentIteration.class, pKey);
        if (docI == null) {
            throw new DocumentIterationNotFoundException(mLocale, pKey);
        } else {
            return docI;
        }
    }

    public DocumentRevision getDocRRef(DocumentRevisionKey pKey) throws DocumentRevisionNotFoundException {
        try {
            return em.getReference(DocumentRevision.class, pKey);
        } catch (EntityNotFoundException pENFEx) {
            LOGGER.log(Level.FINEST,null,pENFEx);
            throw new DocumentRevisionNotFoundException(mLocale, pKey);
        }
    }

    public void createDocR(DocumentRevision pDocumentRevision) throws DocumentRevisionAlreadyExistsException, CreationException {
        try {
            if(pDocumentRevision.getWorkflow()!=null){
                WorkflowDAO workflowDAO = new WorkflowDAO(em);
                workflowDAO.createWorkflow(pDocumentRevision.getWorkflow());
            }

            if(pDocumentRevision.getACL()!=null){
                ACLDAO aclDAO = new ACLDAO(em);
                aclDAO.createACL(pDocumentRevision.getACL());
            }

            //the EntityExistsException is thrown only when flush occurs
            em.persist(pDocumentRevision);
            em.flush();
        } catch (EntityExistsException pEEEx) {
            LOGGER.log(Level.FINEST,null,pEEEx);
            throw new DocumentRevisionAlreadyExistsException(mLocale, pDocumentRevision);
        } catch (PersistenceException pPEx) {
            //EntityExistsException is case sensitive
            //whereas MySQL is not thus PersistenceException could be
            //thrown instead of EntityExistsException
            LOGGER.log(Level.FINEST,null,pPEx);
            throw new CreationException(mLocale);
        }
    }

    public void removeRevision(DocumentRevision pDocR) {
        SubscriptionDAO subscriptionDAO = new SubscriptionDAO(em);
        DocumentDAO docDAO = new DocumentDAO(em);
        subscriptionDAO.removeAllSubscriptions(pDocR);

        WorkflowDAO workflowDAO = new WorkflowDAO(em);
        workflowDAO.removeWorkflowConstraints(pDocR);
        em.flush();

        for(DocumentIteration doc:pDocR.getDocumentIterations()) {
            docDAO.removeDoc(doc);
        }

        SharedEntityDAO sharedEntityDAO = new SharedEntityDAO(em);
        sharedEntityDAO.deleteSharesForDocument(pDocR);

        DocumentMaster docM = pDocR.getDocumentMaster();
        docM.removeRevision(pDocR);

        em.remove(pDocR);
        em.flush();
    }

    public List<DocumentRevision> findDocsWithAssignedTasksForGivenUser(String pWorkspaceId, String assignedUserLogin) {
        return em.createNamedQuery("DocumentRevision.findWithAssignedTasksForUser", DocumentRevision.class)
                .setParameter("workspaceId", pWorkspaceId)
                .setParameter("login", assignedUserLogin)
                .getResultList();
    }

    public List<DocumentRevision> findDocsWithOpenedTasksForGivenUser(String pWorkspaceId, String assignedUserLogin) {
        return em.createNamedQuery("DocumentRevision.findWithOpenedTasksForUser",DocumentRevision.class)
                .setParameter("workspaceId", pWorkspaceId)
                .setParameter("login", assignedUserLogin)
                .getResultList();
    }

    public List<DocumentRevision> findDocsRevisionsWithReferenceOrTitleLike(String pWorkspaceId, String search, int maxResults) {
        return em.createNamedQuery("DocumentRevision.findByReferenceOrTitle",DocumentRevision.class).
                setParameter("workspaceId", pWorkspaceId)
                .setParameter("id", "%" + search + "%")
                .setParameter("title", "%" + search + "%")
                .setMaxResults(maxResults).getResultList();
    }

    public int getTotalNumberOfDocuments(String pWorkspaceId) {
        return ((Number)em.createNamedQuery("DocumentRevision.countByWorkspace")
                .setParameter("workspaceId", pWorkspaceId)
                .getSingleResult()).intValue();
    }

    public long getDiskUsageForDocumentsInWorkspace(String pWorkspaceId) {
        Number result = (Number)em.createNamedQuery("BinaryResource.diskUsageInPath")
                .setParameter("path", pWorkspaceId+"/documents/%")
                .getSingleResult();

        return result != null ? result.longValue() : 0L;

    }

    public long getDiskUsageForDocumentTemplatesInWorkspace(String pWorkspaceId) {
        Number result = (Number)em.createNamedQuery("BinaryResource.diskUsageInPath")
                .setParameter("path", pWorkspaceId+"/document-templates/%")
                .getSingleResult();

        return result != null ? result.longValue() : 0L;

    }

    public List<DocumentRevision> findAllCheckedOutDocRevisions(String pWorkspaceId) {
        TypedQuery<DocumentRevision> query = em.createQuery("SELECT DISTINCT d FROM DocumentRevision d WHERE d.checkOutUser is not null and d.documentMaster.workspace.id = :workspaceId", DocumentRevision.class);
        query.setParameter("workspaceId", pWorkspaceId);
        return query.getResultList();
    }

    public DocumentIteration findDocumentIterationByBinaryResource(BinaryResource pBinaryResource) {
        TypedQuery<DocumentIteration> query = em.createNamedQuery("DocumentIteration.findByBinaryResource", DocumentIteration.class);
        query.setParameter("binaryResource", pBinaryResource);
        try{
            return query.getSingleResult();
        }catch(NoResultException ex){
            LOGGER.log(Level.FINEST,null,ex);
            return null;
        }
    }

    public List<DocumentRevision> getAllDocumentRevisions(String workspaceId) {
        String excludedFolders = workspaceId + "/~%";
        TypedQuery<DocumentRevision> query = em.createNamedQuery("DocumentRevision.findByWorkspace", DocumentRevision.class)
                .setParameter("workspaceId", workspaceId)
                .setParameter("excludedFolders", excludedFolders);
        return query.getResultList();
    }

    public List<DocumentRevision> getDocumentRevisionsFiltered(User user, String workspaceId, int start, int maxResults) {
        String excludedFolders = workspaceId + "/~%";

        TypedQuery<DocumentRevision> query = em.createNamedQuery("DocumentRevision.findByWorkspace.filterACLEntry", DocumentRevision.class)
                        .setParameter("workspaceId", workspaceId)
                        .setParameter("user", user)
                        .setParameter("excludedFolders", excludedFolders);
        if(start>-1 && maxResults >-1){
            query.setFirstResult(start)
                .setMaxResults(Math.min(maxResults, MAX_RESULTS));
        }
        return query.getResultList();
    }

    public int getDocumentRevisionsCountFiltered(User user, String workspaceId) {

        String excludedFolders = workspaceId + "/~%";

        return ((Number) em.createNamedQuery("DocumentRevision.countByWorkspace.filterACLEntry")
                .setParameter("workspaceId", workspaceId)
                .setParameter("user", user)
                .setParameter("excludedFolders", excludedFolders)
                .getSingleResult()).intValue();
    }

    public DocumentRevision getWorkflowHolder(Workflow workflow) {
        try {
            return em.createNamedQuery("DocumentRevision.findByWorkflow", DocumentRevision.class).
                    setParameter("workflow", workflow).getSingleResult();
        }catch(NoResultException e){
            return null;
        }
    }
}
