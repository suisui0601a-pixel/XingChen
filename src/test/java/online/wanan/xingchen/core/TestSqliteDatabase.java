package online.wanan.xingchen.core;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Per-test-class SQLite file with explicit connection shutdown and artifact cleanup. */
public final class TestSqliteDatabase {
    private TestSqliteDatabase(){}
    public static Path create(String purpose){
        try{return Files.createTempDirectory(Path.of("build").toAbsolutePath(),"xingchen-"+purpose+"-").resolve("test.sqlite");}
        catch(IOException e){throw new IllegalStateException("could not create isolated SQLite test directory",e);}
    }
    public static String jdbcUrl(Path db){return "jdbc:sqlite:"+db.toAbsolutePath().toString().replace('\\','/');}
    public static void clean(JdbcTemplate jdbc,Path db){
        try{
            if(jdbc!=null&&jdbc.getDataSource() instanceof HikariDataSource pool)pool.close();
            deleteOwnedDirectory(db);
        }catch(IOException e){throw new IllegalStateException("could not clean isolated SQLite test database",e);}
    }
    public static void clean(Path db){
        try{deleteOwnedDirectory(db);}
        catch(IOException e){throw new IllegalStateException("could not clean isolated SQLite test database",e);}
    }
    private static void deleteOwnedDirectory(Path db)throws IOException{
        Path build=Path.of("build").toAbsolutePath().normalize();Path absolute=db.toAbsolutePath().normalize();Path directory=absolute.getParent();
        boolean ownedBuildDirectory=directory!=null&&build.equals(directory.getParent())
                &&(directory.getFileName().toString().startsWith("xingchen-")||directory.getFileName().toString().startsWith("dsh-restart-"))
                &&(absolute.getFileName().toString().equals("test.sqlite")||absolute.getFileName().toString().equals("runtime.sqlite"));
        if(ownedBuildDirectory){
            for(int attempt=0;attempt<40&&Files.exists(directory);attempt++){
                try{Files.walkFileTree(directory,new SimpleFileVisitor<>(){
                    @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes)throws IOException{
                        deleteWithRetry(file);return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file,IOException failure)throws IOException{
                        if(failure instanceof NoSuchFileException)return FileVisitResult.CONTINUE;
                        throw failure;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir,IOException failure)throws IOException{
                        if(failure!=null&&!(failure instanceof NoSuchFileException))throw failure;
                        try{Files.deleteIfExists(dir);}catch(java.nio.file.DirectoryNotEmptyException ignored){}
                        return FileVisitResult.CONTINUE;
                    }
                });}catch(NoSuchFileException ignored){}
                if(Files.exists(directory))try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("interrupted while cleaning isolated SQLite test directory",interrupted);}
            }
            if(Files.exists(directory))throw new IOException("isolated SQLite test directory remained non-empty");
            return;
        }
        deleteWithRetry(Path.of(absolute+"-wal"));deleteWithRetry(Path.of(absolute+"-shm"));deleteWithRetry(Path.of(absolute+"-journal"));deleteWithRetry(absolute);
        if(directory!=null)try{Files.deleteIfExists(directory);}catch(java.nio.file.DirectoryNotEmptyException ignored){}
    }
    private static void deleteWithRetry(Path path)throws IOException{
        IOException last=null;
        for(int i=0;i<20;i++)try{Files.deleteIfExists(path);return;}catch(IOException e){last=e;try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("interrupted while cleaning SQLite test database",interrupted);}}
        throw last;
    }
}
