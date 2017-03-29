package nablarch.fw.launcher;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * ResultData
 */
@Entity
@Table(name = "ResultData")
public class ResultData {
   
    public ResultData() {
    };
    
    public ResultData(String id, String activity) {
		this.id = id;
		this.activity = activity;
	}

	@Id
    @Column(name = "ID", length = 3, nullable = false)
    public String id;

	@Id
    @Column(name = "ACTIVITY", length = 32, nullable = false)
	public String activity;
}