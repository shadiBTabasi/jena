/*
 * (c) Copyright 2007, 2008, 2009 Hewlett-Packard Development Company, LP
 * (c) Copyright 2011 Epimorphics Ltd.
 * All rights reserved.
 * [See end of file]
 */

package com.hp.hpl.jena.tdb.base.block;

import static java.lang.String.format ;

import java.nio.ByteBuffer ;
import java.util.ArrayList ;
import java.util.LinkedList ;
import java.util.List ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.hp.hpl.jena.tdb.sys.SystemTDB ;

/** Block manager that simulates a disk in-memory - for testing, not written for efficiency.
 * There is a safe mode, whereby blocks are copied in and out to guarantee no writing to an unallocated block.
 * Thisis very inefficient but a better simulation of a disk.
 */
final   // Given the other inefficiencies, this is trivial!
public class BlockMgrMem extends BlockMgrBase
{
    private static final boolean Checking = true ;
    private static Logger log = LoggerFactory.getLogger(BlockMgrMem.class) ;
    private List<Block> blocks = new ArrayList<Block>() ;
    
    // Chain of indexes of free blocks.
    //private Deque<Integer> freeBlocks = new ArrayDeque<Integer>();    // Java6
    
    private LinkedList<Block> freeBlocks = new LinkedList<Block>();
    
    private static Block FreeBlock = new Block(-1, BlockType.FREE, null) ;
    // This controls whether blocks are copied in and out
    public static boolean SafeMode = true ;
    private final boolean safeModeThisMgr ;                            
    
    // Create via the BlockMgrFactory.
    BlockMgrMem(int blockSize)
    {
        this(blockSize, SafeMode) ;
    }
    
    BlockMgrMem(int blockSize, boolean safeMode)
    {
        super(blockSize) ;
        safeModeThisMgr = safeMode ;
    }
    
    @Override
    protected Block allocate(BlockType blockType)
    {
        if ( !freeBlocks.isEmpty() )
        {
            Block block = freeBlocks.removeFirst() ;
            if ( blocks.get(block.getId()) != FreeBlock )
                throw new BlockException("Inconsistent : free chain block is not marked a free") ;
            blocks.set(block.getId(), block) ;
            block.reset(blockType) ;
            return block ;
        }
        
        int x = blocks.size() ;
        ByteBuffer bb = ByteBuffer.allocate(blockSize) ;
        Block block = new Block(x, blockType, bb) ;
        blocks.add(block) ;
        return block;
    }

    @Override
    public Block promote(Block block)
    {
        return null ;
    }

    @Override
    public Block getRead(int id)
    {
        return getBlock(id) ;
    }

    @Override
    public Block getWrite(int id)
    {
        return getBlock(id) ;
    }

    private Block getBlock(int id)
    {
        Block block = blocks.get(id) ;
        if ( block == FreeBlock )
            throw new BlockException("Attempt to get a free block: id = "+id) ;
        if ( safeModeThisMgr )
            block = replicate(block) ;
        return block ;
    }

    @Override
    public void releaseRead(Block block)
    { check(block) ; }

    @Override
    public void releaseWrite(Block block)
    { check(block) ; }

    @Override
    public void put(Block block)
    {
        check(block) ;
        if ( safeModeThisMgr )
            block = replicate(block) ;
        if ( block == FreeBlock )
            throw new BlockException("Attempt to put the free block") ;
        blocks.set(block.getId(), block) ;
    }

    @Override
    public void freeBlock(Block block)
    {
        blocks.set(block.getId(), FreeBlock) ;
        freeBlocks.add(block) ;
    }

    @Override
    public boolean valid(int id)
    {
        if ( id >= blocks.size() )
            return false ;
        if ( id < 0 )
            return false ;

        Block blk = blocks.get(id) ; 
        return (blk != FreeBlock) && (blk != null) ;
    }

    private boolean isFree(int id)
    {
        return blocks.get(id) == FreeBlock ; 
    }
    
    @Override
    public void sync()
    { }
    
    @Override
    public boolean isClosed() { return blocks == null ; }  
    
    @Override
    public void close()
    { 
        blocks = null ;
        freeBlocks = null ;
    }
    
    @Override
    public boolean isEmpty()
    {
        return blocks.size() == 0 ;
    }

    private static Block replicate(Block srcBlock)
    {
        ByteBuffer srcBytes = srcBlock.getByteBuffer() ;
        ByteBuffer dstBytes = replicate(srcBytes) ;
        return new Block(srcBlock.getId(), srcBlock.getType(), dstBytes) ; 
    }  

    private static ByteBuffer replicate(ByteBuffer srcBlk)
    {
        ByteBuffer dstBlk = ByteBuffer.allocate(srcBlk.capacity()) ;
        System.arraycopy(srcBlk.array(), 0, dstBlk.array(), 0, srcBlk.capacity()) ;
        return dstBlk ; 
    }  
    
    private void check(Block block)
    {
        check(block.getId()) ;
        check(block.getByteBuffer()) ;
    }
    
    private void check(int id)
    {
        if ( !Checking ) return ;
        if ( id < 0 || id >= blocks.size() )
            throw new BlockException("BlockMgrMem: Bounds exception: "+id) ;
        if ( isFree(id) )
        {
            isFree(id) ;
            throw new BlockException("BlockMgrMem: Block is the free block: "+id) ;
        }
    }

    private void check(ByteBuffer bb)
    {
        if ( !Checking ) return ;
        if ( bb.capacity() != blockSize )
            throw new BlockException(format("BlockMgrMem: Wrong size block.  Expected=%d : actual=%d", blockSize, bb.capacity())) ;
        if ( bb.order() != SystemTDB.NetworkOrder )
            throw new BlockException("BlockMgrMem: Wrong byte order") ;
        
    }

    @Override
    public String toString() { return format("BlockMgrMem[%d bytes]", blockSize) ; }
    
    @Override
    protected Logger getLog()
    {
        return log ;
    }
}

/*
 * (c) Copyright 2007, 2008, 2009 Hewlett-Packard Development Company, LP
 * (c) Copyright 2011 Epimorphics Ltd.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */