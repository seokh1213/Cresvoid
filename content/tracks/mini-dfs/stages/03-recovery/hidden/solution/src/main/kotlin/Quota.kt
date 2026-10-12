class QuotaCharge(val names: Long, val space: Long)

fun verifyQuota(directoriesDeepestFirst: List<Directory>, charge: QuotaCharge) {
    for (directory in directoriesDeepestFirst) {
        if (charge.names > 0 && directory.nameQuotaExceededBy(charge.names)) reject(ErrorCode.NSQUOTA, directory.path)
        if (charge.space > 0 && directory.spaceQuotaExceededBy(charge.space)) reject(ErrorCode.DSQUOTA, directory.path)
    }
}
