package com.allossimulator.model.memory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Data
public class Memory {
    
    private final long totalSize;
    private final int pageSize;
    private final int pageCount;
    
    private byte[] physicalMemory;
    private BitSet pageAllocationBitmap;
    private Map<Integer, Page> pageTable;
    private Map<Integer, MemoryBlock> allocatedBlocks;
    
    private final AtomicLong usedMemory = new AtomicLong(0);
    private final AtomicLong freeMemory;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Memory statistics
    private long totalAllocations = 0;
    private long totalDeallocations = 0;
    private long totalPageFaults = 0;
    private long totalSwapOuts = 0;
    private long totalSwapIns = 0;
    
    // Cache
    private CacheMemory l1Cache;
    private CacheMemory l2Cache;
    private CacheMemory l3Cache;
    
    // Swap space
    private SwapSpace swapSpace;
    
    public Memory(long totalSize) {
        this(totalSize, 4096); // Default 4KB pages
    }
    
    public Memory(long totalSize, int pageSize) {
        this.totalSize = totalSize;
        this.pageSize = pageSize;
        this.pageCount = (int) (totalSize / pageSize);
        this.freeMemory = new AtomicLong(totalSize);
        
        initialize();
    }
    
    public void initialize() {
        log.info("Initializing memory: {} bytes, {} pages of {} bytes", 
                totalSize, pageCount, pageSize);
        
        // Allocate physical memory
        physicalMemory = new byte[(int) totalSize];
        
        // Initialize page allocation bitmap
        pageAllocationBitmap = new BitSet(pageCount);
        
        // Initialize page table
        pageTable = new ConcurrentHashMap<>();
        
        // Initialize allocated blocks tracking
        allocatedBlocks = new ConcurrentHashMap<>();
        
        // Initialize cache hierarchy
        l1Cache = new CacheMemory("L1", 32 * 1024);      // 32KB L1
        l2Cache = new CacheMemory("L2", 256 * 1024);     // 256KB L2
        l3Cache = new CacheMemory("L3", 8 * 1024 * 1024); // 8MB L3
        
        // Initialize swap space (2x physical memory)
        swapSpace = new SwapSpace(totalSize * 2);
        
        // Reserve first page for system use
        pageAllocationBitmap.set(0);
        
        log.info("Memory initialized successfully");
    }
    
    public MemoryBlock allocate(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid allocation size: " + size);
        }
        
        lock.writeLock().lock();
        try {
            // Calculate required pages
            int pagesNeeded = (int) Math.ceil((double) size / pageSize);
            
            // Find contiguous free pages
            int startPage = findContiguousFreePages(pagesNeeded);
            if (startPage == -1) {
                // Try to free up memory by swapping
                if (!performSwapOut(pagesNeeded)) {
                    throw new OutOfMemoryError("Cannot allocate " + size + " bytes");
                }
                startPage = findContiguousFreePages(pagesNeeded);
                if (startPage == -1) {
                    throw new OutOfMemoryError("Cannot allocate " + size + " bytes after swap");
                }
            }
            
            // Mark pages as allocated
            for (int i = 0; i < pagesNeeded; i++) {
                pageAllocationBitmap.set(startPage + i);
                Page page = new Page(startPage + i);
                page.setAllocated(true);
                pageTable.put(startPage + i, page);
            }
            
            // Create memory block
            MemoryBlock block = new MemoryBlock();
            block.setId(allocatedBlocks.size() + 1);
            block.setStartAddress(startPage * pageSize);
            block.setSize(size);
            block.setPageCount(pagesNeeded);
            block.setStartPage(startPage);
            
            // Update statistics
            usedMemory.addAndGet(pagesNeeded * pageSize);
            freeMemory.addAndGet(-pagesNeeded * pageSize);
            totalAllocations++;
            
            // Store block reference
            allocatedBlocks.put(block.getId(), block);
            
            log.debug("Allocated {} bytes at address 0x{}", size, 
                     Long.toHexString(block.getStartAddress()));
            
            return block;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void free(MemoryBlock block) {
        if (block == null) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            // Clear pages
            for (int i = 0; i < block.getPageCount(); i++) {
                int pageIndex = block.getStartPage() + i;
                pageAllocationBitmap.clear(pageIndex);
                
                Page page = pageTable.remove(pageIndex);
                if (page != null) {
                    page.setAllocated(false);
                    
                    // Clear physical memory
                    Arrays.fill(physicalMemory, 
                               pageIndex * pageSize, 
                               Math.min((pageIndex + 1) * pageSize, physicalMemory.length), 
                               (byte) 0);
                }
            }
            
            // Remove block reference
            allocatedBlocks.remove(block.getId());
            
            // Update statistics
            usedMemory.addAndGet(-block.getPageCount() * pageSize);
            freeMemory.addAndGet(block.getPageCount() * pageSize);
            totalDeallocations++;
            
            // Invalidate cache entries
            l1Cache.invalidate(block.getStartAddress(), block.getSize());
            l2Cache.invalidate(block.getStartAddress(), block.getSize());
            l3Cache.invalidate(block.getStartAddress(), block.getSize());
            
            log.debug("Freed {} bytes at address 0x{}", block.getSize(), 
                     Long.toHexString(block.getStartAddress()));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public byte[] read(long address, int size) {
        lock.readLock().lock();
        try {
            // Check cache hierarchy
            byte[] cached = l1Cache.get(address, size);
            if (cached != null) {
                return cached;
            }
            
            cached = l2Cache.get(address, size);
            if (cached != null) {
                l1Cache.put(address, cached);
                return cached;
            }
            
            cached = l3Cache.get(address, size);
            if (cached != null) {
                l2Cache.put(address, cached);
                l1Cache.put(address, cached);
                return cached;
            }
            
            // Read from physical memory
            if (address + size > totalSize) {
                throw new IllegalArgumentException("Memory access out of bounds");
            }
            
            byte[] data = new byte[size];
            System.arraycopy(physicalMemory, (int) address, data, 0, size);
            
            // Update cache
            l3Cache.put(address, data);
            l2Cache.put(address, data);
            l1Cache.put(address, data);
            
            return data;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void write(long address, byte[] data) {
        lock.writeLock().lock();
        try {
            if (address + data.length > totalSize) {
                throw new IllegalArgumentException("Memory access out of bounds");
            }
            
            // Write to physical memory
            System.arraycopy(data, 0, physicalMemory, (int) address, data.length);
            
            // Update cache (write-through)
            l1Cache.put(address, data);
            l2Cache.put(address, data);
            l3Cache.put(address, data);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private int findContiguousFreePages(int pagesNeeded) {
        int contiguousCount = 0;
        int startIndex = -1;
        
        for (int i = 0; i < pageCount; i++) {
            if (!pageAllocationBitmap.get(i)) {
                if (contiguousCount == 0) {
                    startIndex = i;
                }
                contiguousCount++;
                
                if (contiguousCount >= pagesNeeded) {
                    return startIndex;
                }
            } else {
                contiguousCount = 0;
                startIndex = -1;
            }
        }
        
        return -1;
    }
    
    private boolean performSwapOut(int pagesNeeded) {
        log.debug("Attempting to swap out {} pages", pagesNeeded);
        
        // Find least recently used pages
        List<Page> candidates = new ArrayList<>(pageTable.values());
        candidates.sort(Comparator.comparingLong(Page::getLastAccessTime));
        
        int swappedCount = 0;
        for (Page page : candidates) {
            if (swappedCount >= pagesNeeded) {
                break;
            }
            
            if (page.isAllocated() && !page.isPinned()) {
                // Swap page to disk
                int pageIndex = page.getPageNumber();
                byte[] pageData = new byte[pageSize];
                System.arraycopy(physicalMemory, pageIndex * pageSize, pageData, 0, pageSize);
                
                swapSpace.swapOut(pageIndex, pageData);
                
                // Mark page as swapped
                page.setSwapped(true);
                pageAllocationBitmap.clear(pageIndex);
                
                swappedCount++;
                totalSwapOuts++;
            }
        }
        
        return swappedCount >= pagesNeeded;
    }
    
    public MemoryStatistics getStatistics() {
        return new MemoryStatistics(
            totalSize,
            usedMemory.get(),
            freeMemory.get(),
            totalAllocations,
            totalDeallocations,
            totalPageFaults,
            totalSwapOuts,
            totalSwapIns,
            l1Cache.getHitRate(),
            l2Cache.getHitRate(),
            l3Cache.getHitRate()
        );
    }
    
    @Data
    public static class MemoryBlock {
        private int id;
        private long startAddress;
        private int size;
        private int pageCount;
        private int startPage;
        private boolean locked;
        private long lastAccessTime;
    }
    
    @Data
    public static class Page {
        private final int pageNumber;
        private boolean allocated;
        private boolean dirty;
        private boolean swapped;
        private boolean pinned;
        private long lastAccessTime;
        private int accessCount;
        
        public Page(int pageNumber) {
            this.pageNumber = pageNumber;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
    
    @Data
    public static class CacheMemory {
        private final String name;
        private final int size;
        private final Map<Long, CacheEntry> cache;
        private long hits;
        private long misses;
        
        public CacheMemory(String name, int size) {
            this.name = name;
            this.size = size;
            this.cache = new LinkedHashMap<Long, CacheEntry>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CacheEntry> eldest) {
                    return size() > size / 64; // Cache lines of 64 bytes
                }
            };
        }
        
        public byte[] get(long address, int size) {
            CacheEntry entry = cache.get(address);
            if (entry != null && entry.size >= size) {
                hits++;
                return Arrays.copyOf(entry.data, size);
            }
            misses++;
            return null;
        }
        
        public void put(long address, byte[] data) {
            cache.put(address, new CacheEntry(data));
        }
        
        public void invalidate(long address, int size) {
            cache.remove(address);
        }
        
        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0;
        }
        
        @Data
        private static class CacheEntry {
            private final byte[] data;
            private final int size;
            private final long timestamp;
            
            public CacheEntry(byte[] data) {
                this.data = data;
                this.size = data.length;
                this.timestamp = System.nanoTime();
            }
        }
    }
    
    @Data
    public static class SwapSpace {
        private final long size;
        private final Map<Integer, byte[]> swappedPages;
        
        public SwapSpace(long size) {
            this.size = size;
            this.swappedPages = new ConcurrentHashMap<>();
        }
        
        public void swapOut(int pageNumber, byte[] data) {
            swappedPages.put(pageNumber, data);
        }
        
        public byte[] swapIn(int pageNumber) {
            return swappedPages.remove(pageNumber);
        }
    }
    
    @Data
    public static class MemoryStatistics {
        private final long totalSize;
        private final long usedSize;
        private final long freeSize;
        private final long totalAllocations;
        private final long totalDeallocations;
        private final long totalPageFaults;
        private final long totalSwapOuts;
        private final long totalSwapIns;
        private final double l1HitRate;
        private final double l2HitRate;
        private final double l3HitRate;
    }
}