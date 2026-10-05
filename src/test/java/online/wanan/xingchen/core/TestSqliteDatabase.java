package online.wanan.xingchen.core;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
            Path absolute=db.toAbsolutePath();
            deleteWithRetry(Path.of(absolute+"-wal"));deleteWithRetry(Path.of(absolute+"-shm"));deleteWithRetry(absolute);
            Path directory=absolute.getParent();if(directory!=null)Files.deleteIfExists(directory);
        }catch(IOException e){throw new IllegalStateException("could not clean isolated SQLite test database",e);}
    }
    public static void clean(Path db){
        try{Path absolute=db.toAbsolutePath();deleteWithRetry(Path.of(absolute+"-wal"));deleteWithRetry(Path.of(absolute+"-shm"));deleteWithRetry(absolute);Path directory=absolute.getParent();if(directory!=null)Files.deleteIfExists(directory);}
        catch(IOException e){throw new IllegalStateException("could not clean isolated SQLite test database",e);}
    }
    private static void deleteWithRetry(Path path)throws IOException{
        IOException last=null;
        for(int i=0;i<20;i++)try{Files.deleteIfExists(path);return;}catch(IOException e){last=e;try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("interrupted while cleaning SQLite test database",interrupted);}}
        throw last;
    }
}
