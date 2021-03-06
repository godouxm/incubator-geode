/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.cache.persistence.query;

import com.gemstone.gemfire.internal.cache.CachedDeserializable;

/**
 * The contract for a sorted set of temporary results for a query.
 * This set may be persisted on disk.
 * 
 * This class is threadsafe. Iterators will reflect all entries added to
 * the set up until the time that the iterator was obtained. After that they
 * may or may not reflect modifications to the set while the iteration is in progress.
 * They will guarantee that entries will be returned in the correct order.
 * 
 * The key and value are both allowed to be an object, or a CachedDeserializable.
 * 
 * @author dsmith
 * @since cedar
 */
public interface ResultMap {

  /**
   * Add an entry to the map. If the same key exists in the 
   * map it is replaced with the new value
   * @param key the key for the entry. The key may be NULL.
   * @param value a value for the entry, or NULL for no value
   */
  void put(Object key, Object value);

  /**
   * Remove an entry from the map.
   * @param key the key to remove
   * 
   * This method has no effect if the key does not exist
   * in the map.
   */
  void remove(Object key);

  /**
   * Return the Entry for a given key
   */
  Entry getEntry(Object key);
  
  /**
   * Return the value for a given key
   */
  CachedDeserializable get(Object key);
  
  /**
   * return true if this map contains the given key 
   */ 
  public boolean containsKey(Object e);
  
  /**
   * Return all of the IndexEntries in the range between start and end. 
   * If end < start, this will return a descending iterator going from end
   * to start. 
   */
  CloseableIterator<Entry> iterator(Object start, boolean startInclusive, 
      Object end, boolean endInclusive);
  
  /**
   * Return all of the IndexEntries that from start to the tail of the map.
   */
  CloseableIterator<Entry> iterator(Object start, boolean startInclusive);
  
  /**
   * Return all of the IndexEntries in the map.
   */
  CloseableIterator<Entry> iterator();
  
  /**
   * Return all of the region keys from start to end.
   */
  CloseableIterator<CachedDeserializable> keyIterator(Object start, 
      boolean startInclusive, Object end, boolean endInclusive);
  
  /**
   * Return all of the region keys from start to the tail of the map
   */
  CloseableIterator<CachedDeserializable> keyIterator(Object start, 
      boolean startInclusive);
  
  /**
   * Return all of the region keys in the map
   */
  CloseableIterator<CachedDeserializable> keyIterator();
  
  /**
   * Close the map to free up resources.
   */
  void close();

  /**
   * A single entry in an index
   * @author dsmith
   * @since cedar
   */
  interface Entry {
    /**
     * Return the index key of the entry. May be NULL.
     */
    CachedDeserializable getKey();
    /**
     * Return the value of the entry. May be NULL.
     */
    CachedDeserializable getValue();
  }
}
