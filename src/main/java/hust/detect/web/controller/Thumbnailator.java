package hust.detect.web.controller;

import java.io.File;
import java.io.IOException;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.name.Rename;

public class Thumbnailator {

	public static void main(String[] args) {
		try {
			Thumbnails.of(new File("D:\\pic").listFiles()).
				scale(0.5). // 图片缩放80%, 不能和size()一起使用
				toFiles(new File("D:\\pic"), Rename.SUFFIX_HYPHEN_THUMBNAIL);// 指定的目录一定要存在,否则报错
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
