package nablarch.fw.launcher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * InputData
 */
@Entity
@Table(name = "InputData")
public class InputData {
   
    public InputData() {
    };
    
    public InputData(String id) {
		this.id = id;
	}

	@Id
    @Column(name = "ID", length = 3, nullable = false)
    public String id;
}