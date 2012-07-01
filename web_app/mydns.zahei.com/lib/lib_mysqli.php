<?php
/**
 * Mysqli类
 *
 * @author 废墟
 * @version v1.0 2009-08-18
 * @link http://anerg.cn/
 */
class lib_mysqli {
	protected $mysqli;
	protected $sql;
	protected $rs;
	protected $query_num	= 0;
	protected $fetch_mode	= MYSQLI_ASSOC;
	protected $cache_type	= 'file';
	protected $cache_dir	= './cache/';
	protected $cache_time	= 1800;
	protected $reload_cache	= false;

	public function  __construct($dbhost, $dbuser, $dbpass, $dbname, $dbport) {
		$this->mysqli    = new mysqli($dbhost, $dbuser, $dbpass, $dbname, $dbport);
		if(mysqli_connect_errno()) {
			$this->mysqli    = false;
			echo '<h2>'.mysqli_connect_error().'</h2>';
			die();
		} else {
			$this->mysqli->set_charset("utf8");
		}
	}
	public function  __destruct() {
		$this->free();
		$this->close();
	}
	protected function free() {
		@$this->rs->free();
	}
	protected function close() {
		$this->mysqli->close();
	}
	protected function fetch() {
		return $this->rs->fetch_array($this->fetch_mode);
	}
	protected function getQuerySql($sql, $limit = null) {
		if (@preg_match("/[0-9]+(,[ ]?[0-9]+)?/is", $limit) && !preg_match("/ LIMIT [0-9]+(,[ ]?[0-9]+)?$/is", $sql)) {
			$sql .= " LIMIT " . $limit;
		}
		return $sql;
	}
	protected function get_cache($sql,$method) {
		$cache	= lib_cache::T($this->cache_type);
		$key	= $sql.$method;
		if($this->cache_type == 'file') {
			$cache->set_cache_dir($this->cache_dir);
		}
		$cache->set_cache_time($this->cache_time);
		$res    = $cache->get_cache($key);
		if($this->reload_cache || !$res) {
			$res    = $this->$method($sql);
			$cache->set_cache($key, $res);
		}
		return $res;
	}
	public function query_num() {
		return $this->query_num;
	}
	public function set_cache_type($cache_type) {
		$this->cache_type	= $cache_type;
	}
	public function set_cache_dir($cache_dir) {
		$this->cache_dir    = $cache_dir;
	}
	public function set_cache_time($cache_time) {
		$this->cache_time    = $cache_time;
	}
	public function query($sql, $limit = null) {
		$sql    = $this->getQuerySql($sql, $limit);
		$this->sql    = $sql;
		$this->rs    = $this->mysqli->query($sql);
		if (!$this->rs) {
			echo "<h2>".$this->mysqli->error."<br/>\nsql=".$sql."\n<br/></h2>";
			die();
		} else {
			$this->query_num++;
			return $this->rs;
		}
	}
	/**
     * 取得结果集中的第一列
     *
     * @param string $sql SQL 语句
     * @param mixed $limit 根据接收的 limit 变量来限制结果集
     * @return array
     */
    public function getCol($sql, $limit = null) {
        $this->query($sql, $limit);
		$this->fetch_mode    = MYSQLI_NUM;
        $result = array();
		//print_r($this->fetch());
        while ($rows = $this->fetch()) {
            $result[] = $rows[0];
        }
        $this->free();
        return $result;
    }
	function getCol2( $sql, $limit = null ){
		$this->query($sql, $limit);
		$this->fetch_mode    = MYSQLI_NUM;
        $result = array();
		//print_r($this->fetch());
        while ($rows = $this->fetch()) {
            $result[ $rows[0]] = $rows[1];
        }
        $this->free();
        return $result;
	}
	

	public function getOne($sql) {
		$this->query($sql, 1);
		$this->fetch_mode    = MYSQLI_NUM;
		$row = $this->fetch();
		$this->free();
		return $row[0];
	}
	public function get_one($sql) { return $this->getOne($sql); }
	public function cache_one($sql, $reload = false) {
		$this->reload_cache	= $reload;
		$sql    = $this->getQuerySql($sql, 1);
		return $this->get_cache($sql, 'getOne');
	}
	public function getRow($sql, $fetch_mode = MYSQLI_ASSOC) {
		$this->query($sql, 1);
		$this->fetch_mode    = $fetch_mode;
		$row = $this->fetch();
		$this->free();
		return $row;
	}
	public function get_row($sql, $fetch_mode = MYSQLI_ASSOC) { return $this->getRow($sql); }
	public function cache_row($sql, $reload = false) {
		$this->reload_cache	= $reload;
		$sql    = $this->getQuerySql($sql, 1);
		return $this->get_cache($sql, 'getRow');
	}
	public function getAll($sql, $limit = null, $fetch_mode = MYSQLI_ASSOC) {
		$this->query($sql, $limit);
		$all_rows = array();
		$this->fetch_mode    = $fetch_mode;
		while($rows = $this->fetch()) {
			$all_rows[] = $rows;
		}
		$this->free();
		return $all_rows;
	}
	public function getAll2($sql, $limit = null, $fetch_mode = MYSQLI_ASSOC) {
		$this->query($sql, $limit);
		$all_rows = array();
		$this->fetch_mode    = $fetch_mode;
		while($rows = $this->fetch()) {
			$all_rows[$rows[0]] = $rows;
		}
		$this->free();
		return $all_rows;
	}
	public function get_all($sql, $limit = null, $fetch_mode = MYSQLI_ASSOC) { return $this->getAll($sql); }
	public function cache_all($sql, $reload = false, $limit = null) {
		$this->reload_cache	= $reload;
		$sql    = $this->getQuerySql($sql, $limit);
		return $this->get_cache($sql, 'getAll');
	}
	public function insert_id() {
		return $this->mysqli->insert_id;
	}
	public function lastID() {
		return $this->insert_id();
	}
	public function escape($str) {
		if(is_array($str)) {
			foreach($str as $key=>$val) {
				$str[$key] = $this->escape($val);
			}
		} else {
			$str = addslashes(trim($str));
		}
		return $str;
	}
}
////用法
//$db    = new db_mysqli('localhost', 'root', 111222, 'dict');
//$db->set_cache_time(10);
//$db->set_cache_dir('./cache/sql/');
//$sql = "select * from words order by word_id limit 10,10";
//$res1 = $db->get_all($sql);
//$res2 = $db->cache_all($sql);
//
//echo $db->query_num(),'<br>';
?>