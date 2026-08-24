# Technical Services CDP Onboarding Capstone: Product Catalog Integration

## **1. The Business Scenario**

Our customer, **WonderDrugs**, is undergoing a digital transformation. To manage their global list of therapeutics, they have established a custom object in Veeva Vault called **Therapeutic Product** (`therapeutic_product__c`).

**WonderDrugs** will have a separate integration that will update the **products.csv** document in the vault with updated therapeutics.

A Scheduled Vault SDK Job called **Product Catalog Loader** is configured to execute behind it. This job is supposed to query the Document Library for the latest version of `products.csv`, download the file stream natively using a secure local loopback request, parse the columns, and populate the `therapeutic_product__c` object records.

### **The Problem:**
WonderDrugs has reported that the integration job is **failing completely at runtime** today. No new products are appearing in their catalog. 

Your task is to investigate the runtime error and remediate it.

---

## **2. Helpful Information**

### **Staging more data**
*  More test data can be staged by updating the: **`products.csv`** document in your vault

---
