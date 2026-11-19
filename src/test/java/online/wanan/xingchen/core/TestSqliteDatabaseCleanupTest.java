package online.wanan.xingchen.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class TestSqliteDatabaseCleanupTest {
    @Test void fileDisappearingDuringTraversalIsAlreadyCleaned()throws Exception{
        Path vanished=Path.of("build","synthetic.sqlite-wal");
        assertThat(TestSqliteDatabase.cleanupVisitor().visitFileFailed(vanished,new NoSuchFileException(vanished.toString())))
            .isEqualTo(FileVisitResult.CONTINUE);
    }
    @Test void unrelatedTraversalFailureStillFails()throws Exception{
        Path file=Path.of("build","synthetic.sqlite");
        for(IOException failure:new IOException[]{new AccessDeniedException(file.toString()),new IOException("synthetic IO failure")}){
            assertThatThrownBy(()->TestSqliteDatabase.cleanupVisitor().visitFileFailed(file,failure)).isSameAs(failure);
            assertThatThrownBy(()->TestSqliteDatabase.cleanupVisitor().postVisitDirectory(file,failure)).isSameAs(failure);
        }
    }
    @Test void ownedCleanupRemovesNestedSidecarsAndIsIdempotent()throws Exception{
        Path db=TestSqliteDatabase.create("cleanup-regression"),root=db.getParent();
        try{
            Files.writeString(db,"synthetic");Files.writeString(Path.of(db+"-wal"),"synthetic");
            Files.writeString(Path.of(db+"-shm"),"synthetic");
            Path nested=Files.createDirectory(root.resolve("nested"));Files.writeString(nested.resolve("artifact"),"synthetic");
            TestSqliteDatabase.clean(db);assertThat(root).doesNotExist();
            TestSqliteDatabase.clean(db);assertThat(root).doesNotExist();
        }finally{TestSqliteDatabase.clean(db);}
    }
}
