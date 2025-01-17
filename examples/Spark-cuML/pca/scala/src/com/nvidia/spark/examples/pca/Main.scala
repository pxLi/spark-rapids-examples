/*
 * Copyright (c) 2021, NVIDIA CORPORATION. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.nvidia.spark.examples.pca

import org.apache.spark.ml.linalg._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, udf}

object Main {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName("PCA Example").getOrCreate()
    val dim = 2048
    val rows = 100000
    val r = new scala.util.Random(0)

    // generate dummy data
    val dataDf = spark.createDataFrame(
      (0 until rows).map(_ => Tuple1(Array.fill(dim)(r.nextDouble)))).withColumnRenamed("_1", "feature")

    val pcaGpu = new com.nvidia.spark.ml.feature.PCA().setInputCol("feature").setOutputCol("pca_features").setK(3)
    // GPU train
    val gpuStart = System.currentTimeMillis()
    val pcaModelGpu = pcaGpu.fit(dataDf)
    val gpuEnd = System.currentTimeMillis()

    // use original Spark ML PCA class
    val pcaCpu = new org.apache.spark.ml.feature.PCA().setInputCol("feature").setOutputCol("pca_features").setK(3)

    // use udf to meet standard CPU ML algo input requirement: Vector input
    val convertToVector = udf((array: Seq[Double]) => {
      Vectors.dense(array.map(_.toDouble).toArray)
    })

    val vectorDf = dataDf.withColumn("feature_vec", convertToVector(col("feature")))
    // CPU train
    
    val cpuStart = System.currentTimeMillis()
    val pcaModelCpu = pcaCpu.fit(vectorDf)
    val cpuEnd = System.currentTimeMillis()

    
    println("GPU training: ")
    println( (gpuEnd - gpuStart) / 1000 + " seconds")
    println("CPU training: ")
    println( (cpuEnd - cpuStart) / 1000 + " seconds")

    // transform
    pcaModelGpu.transform(vectorDf).select("pca_features").show(false)
    pcaModelCpu.transform(vectorDf).select("pca_features").show(false)
  }
}
