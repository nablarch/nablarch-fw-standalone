package nablarch.fw.launcher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * BOOK_BACKUP
 */
@Entity
@Table(name = "BOOK_BACKUP")
public class BookBackup {
   
    public BookBackup() {
    };
    
    public BookBackup(String title, String publisher, String authors) {
		this.title = title;
		this.publisher = publisher;
		this.authors = authors;
	}

	@Id
    @Column(name = "TITLE", length = 128, nullable = false)
    public String title;
    
    @Column(name = "PUBLISHER", length = 128, nullable = false)
    public String publisher;
    
    @Column(name = "AUTHORS", length = 256, nullable = false)
    public String authors; 
    
    @Column(name = "LAST_UPDATE_USER", length = 64, nullable = false)
    public String lastUpdateUser;
}