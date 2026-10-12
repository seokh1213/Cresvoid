package cresvoid

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException

fun describeIoError(error: IOException): String = when (error) {
    is NoSuchFileException -> "파일이 없습니다: ${error.file}"
    is AccessDeniedException -> "권한이 없습니다: ${error.file}"
    is FileAlreadyExistsException -> "이미 있는 파일입니다: ${error.file}"
    is NotDirectoryException -> "디렉터리가 아닙니다: ${error.file}"
    is DirectoryNotEmptyException -> "비어 있지 않은 디렉터리입니다: ${error.file}"
    is FileSystemException -> listOfNotNull(error.file, error.reason ?: error.javaClass.simpleName).joinToString(": ")
    else -> error.message ?: error.toString()
}
