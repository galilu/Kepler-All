package com.kepler.transaction.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.kepler.config.PropertiesUtils;
import com.kepler.org.apache.commons.lang.StringUtils;
import com.kepler.serial.Serials;
import com.kepler.transaction.TranscationPersistent;
import com.kepler.transaction.TranscationRequest;

/**
 * @author KimShen
 *
 */
public class FilePersistent implements TranscationPersistent {

	/**
	 * 任务恢复时的数量限制(防止OOM)
	 */
	private static final int MAX = PropertiesUtils.get(FilePersistent.class.getName().toLowerCase() + ".max", Integer.MAX_VALUE);

	/**
	 * 文件
	 */
	private static final int BUFFER = PropertiesUtils.get(FilePersistent.class.getName().toLowerCase() + ".buffer", 500);

	/**
	 * 默认最大超时
	 */
	private static final String DIR = PropertiesUtils.get(FilePersistent.class.getName().toLowerCase() + ".dir", null);

	private static final Log LOGGER = LogFactory.getLog(FilePersistent.class);

	private final Serials serials;

	public FilePersistent(Serials serials) {
		super();
		this.serials = serials;
	}

	/**
	 * 定位文件位置
	 * 
	 * @param uuid 事务编号
	 * @return
	 */
	private File location(String uuid) {
		return StringUtils.isEmpty(FilePersistent.DIR) ? new File(uuid) : new File(FilePersistent.DIR, uuid);
	}

	/**
	 * 恢复请求
	 * 
	 * @param requests
	 * @param each
	 */
	private void restore(List<TranscationRequest> requests, File each) {
		try (InputStream input = new FileInputStream(each)) {
			// 如果恢复数量未达到阈值则加载
			if (requests.size() <= FilePersistent.MAX) {
				// 使用默认序列化策略反序列化
				requests.add(this.serials.def4input().input(input, FilePersistent.BUFFER, TranscationRequest.class));
			} else {
				FilePersistent.LOGGER.warn("Too many restored requests (" + FilePersistent.MAX + ") ... ");
			}
		} catch (Throwable e) {
			FilePersistent.LOGGER.error(e.getMessage(), e);
		}
	}

	@Override
	public boolean persist(TranscationRequest request) {
		// 序列化至磁盘
		try (FileOutputStream stream = new FileOutputStream(this.location(request.uuid()))) {
			// 使用默认序列化策略序列化
			this.serials.def4output().output(request, TranscationRequest.class, stream, FilePersistent.BUFFER);
			return true;
		} catch (Throwable e) {
			FilePersistent.LOGGER.error(e.getMessage(), e);
			return false;
		}
	}

	public boolean release(String uuid) {
		File file = this.location(uuid);
		// 如果文件存在则删除
		if (file.exists()) {
			file.delete();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public List<TranscationRequest> list() {
		List<TranscationRequest> requests = new ArrayList<TranscationRequest>();
		// 遍历还未删除的请求
		for (File each : new File(StringUtils.isEmpty(FilePersistent.DIR) ? "." : FilePersistent.DIR).listFiles()) {
			if (each.isFile()) {
				this.restore(requests, each);
			}
		}
		return requests;
	}
}
