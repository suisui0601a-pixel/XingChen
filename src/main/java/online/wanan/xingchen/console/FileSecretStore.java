package online.wanan.xingchen.console;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import online.wanan.xingchen.storage.DataPathResolver;

/** Atomic owner-only secret files; a separate tombstone makes Clear survive environment bootstrap on restart. */
@Component
public final class FileSecretStore implements SecretStore {
    private final Path root;
    private final Environment environment;
    @Autowired public FileSecretStore(DataPathResolver paths,Environment environment){this.root=paths.config();this.environment=environment;}
    FileSecretStore(String root,Environment environment){this.root=Path.of(root).toAbsolutePath().normalize();this.environment=environment;}
    @Override public synchronized Optional<String> read(String name){if(Files.exists(disabled(name)))return Optional.empty();Path file=file(name);if(!Files.isRegularFile(file))return Optional.empty();try{return Optional.of(Files.readString(file));}catch(IOException e){throw new IllegalStateException("credential is unavailable");}}
    @Override public boolean configured(String name){return read(name).filter(s->!s.isBlank()).isPresent();}
    @Override public synchronized void replace(String name,String value){checkName(name);if(value==null||value.isBlank()||value.length()>4096||!value.equals(value.strip())||value.contains("\n")||value.contains("\r"))throw new IllegalArgumentException("credential is invalid");Path temp=null;try{Files.createDirectories(root);secure(root,true);temp=Files.createTempFile(root,"."+name+"-",".tmp");secure(temp,false);Files.writeString(temp,value,StandardOpenOption.TRUNCATE_EXISTING);Files.move(temp,file(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);secure(file(name),false);Files.deleteIfExists(disabled(name));}catch(IOException|UnsupportedOperationException e){throw new IllegalStateException("owner-only credential storage is unavailable; credential was not saved");}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}}
    @Override public synchronized void clear(String name){checkName(name);Path tmp=null;try{Files.createDirectories(root);secure(root,true);tmp=Files.createTempFile(root,"."+name+"-disabled-",".tmp");secure(tmp,false);Files.move(tmp,disabled(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);Files.deleteIfExists(file(name));}catch(IOException|UnsupportedOperationException e){throw new IllegalStateException("credential could not be cleared securely");}finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(IOException ignored){}}}
    @Override public synchronized boolean initialized(String name){return Files.exists(file(name))||Files.exists(disabled(name));}
    @Override public synchronized void bootstrapEnvironment(String name,String environmentVariable){checkName(name);if(initialized(name))return;String value=environment.getProperty(environmentVariable,"");if(!value.isBlank())replace(name,value);}
    private Path file(String name){checkName(name);return root.resolve(name+".secret");}private Path disabled(String name){checkName(name);return root.resolve(name+".cleared");}
    private static void checkName(String name){if(name==null||!name.matches("[a-z][a-z0-9-]{0,63}"))throw new IllegalArgumentException("invalid credential name");}
    private static void secure(Path path,boolean directory)throws IOException{
        try{Files.setPosixFilePermissions(path,directory?EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE):EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));return;}catch(UnsupportedOperationException ignored){}
        AclFileAttributeView acl=Files.getFileAttributeView(path,AclFileAttributeView.class);if(acl==null)throw new UnsupportedOperationException("no owner-only ACL support");UserPrincipal owner=Files.getOwner(path);AclEntry entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();acl.setAcl(List.of(entry));
    }
}
